package com.pulsepass.backofficegateway.unit.api.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pulsepass.backofficegateway.api.controllers.OnboardingController;
import com.pulsepass.backofficegateway.api.exceptions.GlobalExceptionHandler;
import com.pulsepass.backofficegateway.infrastructure.proxies.ServiceRequestException;
import com.pulsepass.backofficegateway.infrastructure.proxies.onboardingservice.OnboardingServiceProxy;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OnboardingController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "spring.messages.basename=messages",
        "spring.messages.fallback-to-system-locale=false"
})
class OnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OnboardingServiceProxy onboardingServiceProxy;

    @Test
    void createLegalPerson_targetResponds_returnsSameStatusBodyAndContentType() throws Exception {
        when(onboardingServiceProxy.forward(
                eq(HttpMethod.POST), eq("/onboarding/legal_persons"), any(HttpServletRequest.class), any()))
                .thenReturn(ResponseEntity.status(409)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"ONB00001\"}".getBytes()));

        mockMvc.perform(post("/legal_persons").contentType(MediaType.APPLICATION_JSON).content("{\"a\":1}"))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/json"))
                .andExpect(content().string("{\"code\":\"ONB00001\"}"));
    }

    @Test
    void createLegalPerson_body_forwardsRawBytesToOnboardingPath() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(201).body(new byte[0]));

        mockMvc.perform(post("/legal_persons").contentType(MediaType.APPLICATION_JSON).content("{\"a\":1}"))
                .andExpect(status().isCreated());

        verify(onboardingServiceProxy).forward(
                eq(HttpMethod.POST),
                eq("/onboarding/legal_persons"),
                any(HttpServletRequest.class),
                eq("{\"a\":1}".getBytes()));
    }

    @Test
    void createLegalPerson_noBody_forwardsNullBody() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any()))
                .thenReturn(ResponseEntity.status(400).body(new byte[0]));

        mockMvc.perform(post("/legal_persons")).andExpect(status().isBadRequest());

        verify(onboardingServiceProxy).forward(
                eq(HttpMethod.POST), eq("/onboarding/legal_persons"), any(HttpServletRequest.class), isNull());
    }

    @Test
    void createLegalPerson_proxyFails_returns500Gen00500() throws Exception {
        when(onboardingServiceProxy.forward(any(), any(), any(), any()))
                .thenThrow(new ServiceRequestException(new IllegalStateException("down")));

        mockMvc.perform(post("/legal_persons").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("GEN00500"))
                .andExpect(jsonPath("$.extra_fields").isEmpty());
    }

    @Test
    void createLegalPerson_getMethod_returns405Gen00405() throws Exception {
        mockMvc.perform(get("/legal_persons"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("GEN00405"));
    }

    @Test
    void createLegalPerson_unknownRoute_returns404Gen00404() throws Exception {
        mockMvc.perform(get("/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GEN00404"));
    }
}
