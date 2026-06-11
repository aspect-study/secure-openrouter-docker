package com.openrouter.gateway.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openrouter.gateway.agent.AgentService;
import com.openrouter.gateway.agent.model.AgentRequest;
import com.openrouter.gateway.agent.model.AgentResponse;
import com.openrouter.gateway.agent.model.ToolStep;
import com.openrouter.gateway.auth.JwtUtil;
import com.openrouter.gateway.auth.UserRepository;
import com.openrouter.gateway.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AgentController.class)
@Import(SecurityConfig.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AgentService agentService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserRepository userRepository;

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void adminUser_returns200WithAgentResponse() throws Exception {
        AgentResponse agentResponse = new AgentResponse(
                "There were 42 requests today.",
                List.of(new ToolStep("get_gateway_stats", Map.of(), Map.of("totalRequests", 42))),
                "meta-llama/llama-3.3-70b-instruct:free"
        );
        when(agentService.run(any(AgentRequest.class), anyString(), any())).thenReturn(agentResponse);

        mockMvc.perform(post("/api/agent/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AgentRequest("How many requests today?", null, null))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = "USER")
    void nonAdminUser_returns403() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AgentRequest("Hello", null, null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void missingQuestion_returns400() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\",\"model\":null}"))
                .andExpect(status().isBadRequest());
    }
}
