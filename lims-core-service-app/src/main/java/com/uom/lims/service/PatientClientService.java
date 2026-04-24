package com.uom.lims.service;

import com.uom.lims.api.patient.dto.response.PatientResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class PatientClientService {

    private final RestTemplate restTemplate;

    // WHY: Base URL is configurable so it works in both local and deployed environments
    @Value("${app.services.patient-base-url:http://localhost:11000}")
    private String patientBaseUrl;

    /**
     * WHY: Patient demographics are owned by the patient module — we fetch them
     * at response time rather than duplicating data across modules
     */
    public PatientResponse getPatientByCode(String patientCode, String bearerToken) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", bearerToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<PatientResponse> response = restTemplate.exchange(
                patientBaseUrl + "/api/v1/patients/" + patientCode,
                HttpMethod.GET,
                entity,
                PatientResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            // WHY: Graceful degradation — if patient service is unavailable,
            // return null so order/bill response still works with null patient fields
            return null;
        }
    }
}
