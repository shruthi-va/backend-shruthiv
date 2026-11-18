package com.example.claims;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:claims-test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "claims.auth.claimant-password=test-claimant",
        "claims.auth.staff-password=test-staff",
        "claims.auth.manager-password=test-manager"
})
@AutoConfigureMockMvc
class ClaimsWorkflowTest {
    private static final String CLAIMS = "/api/claims";
    private static final String SUBMISSION = """
            {"policyNumber":"POL-123","incidentDate":"2024-01-15","description":"Example loss"}
            """;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClaimRepository repository;

    @BeforeEach
    void clearClaims() {
        repository.deleteAll();
    }

    @ParameterizedTest
    @CsvSource({"APPROVE, APPROVED", "DENY, DENIED"})
    void movesThroughWorkflowAndKeepsInternalNotesPrivate(String decision, String finalStatus) throws Exception {
        String claim = submit();

        mvc.perform(get(CLAIMS).with(user("staff").roles("STAFF")))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mvc.perform(get(claim).with(user("staff").roles("STAFF"))).andExpect(status().isNotFound());
        mvc.perform(get(claim).with(user("someone-else").roles("CLAIMANT"))).andExpect(status().isNotFound());
        mvc.perform(get(CLAIMS).with(user("someone-else").roles("CLAIMANT")))
                .andExpect(jsonPath("$.page.totalElements").value(0));

        String finalDecision = "{\"decision\":\"" + decision + "\",\"reason\":\"Final coverage assessment\"}";
        mvc.perform(post(claim + "/decision").with(user("manager").roles("MANAGER")).with(csrf())
                        .contentType("application/json").content(finalDecision))
                .andExpect(status().isConflict());
        mvc.perform(post(claim + "/assignment").with(user("manager").roles("MANAGER")).with(csrf())
                        .contentType("application/json").content("{\"assignee\":\"staff\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ASSIGNED"));
        mvc.perform(get(CLAIMS).with(user("staff").roles("STAFF")))
                .andExpect(jsonPath("$.page.totalElements").value(1));

        String review = "{\"recommendation\":\"APPROVE\",\"reason\":\"Internal review notes\"}";
        mvc.perform(post(claim + "/review").with(user("different-staff").roles("STAFF")).with(csrf())
                        .contentType("application/json").content(review))
                .andExpect(status().isNotFound());
        mvc.perform(post(claim + "/review").with(user("staff").roles("STAFF")).with(csrf())
                        .contentType("application/json").content(review))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVIEWED"));
        mvc.perform(post(claim + "/decision").with(user("manager").roles("MANAGER")).with(csrf())
                        .contentType("application/json").content(finalDecision))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(finalStatus));
        mvc.perform(post(claim + "/decision").with(user("manager").roles("MANAGER")).with(csrf())
                        .contentType("application/json").content(finalDecision))
                .andExpect(status().isConflict());

        mvc.perform(get(claim).with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history[*].status").value(
                        org.hamcrest.Matchers.contains("SUBMITTED", "ASSIGNED", "REVIEWED", finalStatus)))
                .andExpect(jsonPath("$.history[2].actor").value("staff"))
                .andExpect(jsonPath("$.history[2].occurredAt").isNotEmpty());
        mvc.perform(get(claim).with(user("claimant").roles("CLAIMANT")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(finalStatus))
                .andExpect(jsonPath("$.decisionReason").value("Final coverage assessment"))
                .andExpect(jsonPath("$.recommendation").value(nullValue()))
                .andExpect(jsonPath("$.reviewReason").value(nullValue()))
                .andExpect(jsonPath("$.history").value(nullValue()));
    }

    @Test
    void authenticatesAndRequiresCsrfForWrites() throws Exception {
        mvc.perform(get(CLAIMS)).andExpect(status().isUnauthorized());
        mvc.perform(post(CLAIMS).with(httpBasic("claimant", "test-claimant"))
                        .contentType("application/json").content(SUBMISSION))
                .andExpect(status().isForbidden());

        var result = mvc.perform(get("/api/csrf").with(httpBasic("claimant", "test-claimant")))
                .andExpect(status().isOk()).andReturn();
        var token = json.readTree(result.getResponse().getContentAsString());
        mvc.perform(post(CLAIMS).with(httpBasic("claimant", "test-claimant"))
                        .session((MockHttpSession) result.getRequest().getSession(false))
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType("application/json").content(SUBMISSION))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsWrongRoleAndInvalidInput() throws Exception {
        mvc.perform(post(CLAIMS).with(user("staff").roles("STAFF")).with(csrf())
                        .contentType("application/json").content(SUBMISSION))
                .andExpect(status().isForbidden());
        mvc.perform(post(CLAIMS).with(user("claimant").roles("CLAIMANT")).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(CLAIMS + "?size=101").with(user("manager").roles("MANAGER")))
                .andExpect(status().isBadRequest());
    }

    private String submit() throws Exception {
        return mvc.perform(post(CLAIMS).with(user("claimant").roles("CLAIMANT")).with(csrf())
                        .contentType("application/json").content(SUBMISSION))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse().getHeader("Location");
    }
}
