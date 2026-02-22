package com.uom.lims.patientdocument;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uom.lims.api.common.PageResponse;
import com.uom.lims.api.common.enums.DocumentType;
import com.uom.lims.api.document.dto.request.DocumentUpdateRequest;
import com.uom.lims.api.document.dto.response.DocumentResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PatientDocumentController.class)
@AutoConfigureMockMvc
class PatientDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PatientDocumentService patientDocumentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "frontdesk", roles = { "FRONT_DESK" })
    void uploadDocument_ShouldReturnCreatedDocument() throws Exception {
        String patientCode = "P12345";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "dummy content".getBytes());

        DocumentResponse response = DocumentResponse.builder()
                .documentId(UUID.randomUUID())
                .documentType(DocumentType.LAB_REPORT)
                .originalFileName("test.pdf")
                .description("Test Report")
                .build();

        when(patientDocumentService.uploadDocument(
                eq(patientCode),
                eq(DocumentType.LAB_REPORT),
                eq("Test Report"),
                any(),
                any())).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/patients/{patientCode}/documents", patientCode)
                .file(file)
                .param("documentType", "LAB_REPORT")
                .param("description", "Test Report")
                .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFileName").value("test.pdf"))
                .andExpect(jsonPath("$.documentType").value("LAB_REPORT"));
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void listDocuments_ShouldReturnPageOfDocuments() throws Exception {
        String patientCode = "P12345";
        DocumentResponse doc = DocumentResponse.builder()
                .documentId(UUID.randomUUID())
                .documentType(DocumentType.PRESCRIPTION)
                .build();

        Page<DocumentResponse> page = new PageImpl<>(Collections.singletonList(doc));

        when(patientDocumentService.listDocuments(eq(patientCode), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/patients/{patientCode}/documents", patientCode)
                .param("page", "0")
                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].documentType").value("PRESCRIPTION"));
    }

    @Test
    @WithMockUser(username = "mlt", roles = { "MLT" })
    void downloadDocument_ShouldReturnDownloadUrl() throws Exception {
        String patientCode = "P12345";
        UUID documentId = UUID.randomUUID();
        String downloadUrl = "https://s3.amazonaws.com/bucket/key";

        when(patientDocumentService.generateDownloadUrl(patientCode, documentId))
                .thenReturn(downloadUrl);

        mockMvc.perform(get("/api/v1/patients/{patientCode}/documents/{documentId}/download", patientCode, documentId))
                .andExpect(status().isOk())
                .andExpect(content().string(downloadUrl));
    }

    @Test
    @WithMockUser(username = "frontdesk", roles = { "FRONT_DESK" })
    void updateDocument_ShouldReturnUpdatedDocument() throws Exception {
        String patientCode = "P12345";
        UUID documentId = UUID.randomUUID();
        String newDescription = "Updated Description";

        DocumentUpdateRequest request = new DocumentUpdateRequest();
        request.setDescription(newDescription);

        DocumentResponse response = DocumentResponse.builder()
                .documentId(documentId)
                .description(newDescription)
                .build();

        when(patientDocumentService.updateDocument(
                eq(patientCode),
                eq(documentId),
                eq(newDescription),
                any())).thenReturn(response);

        mockMvc.perform(patch("/api/v1/patients/{patientCode}/documents/{documentId}", patientCode, documentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(newDescription));
    }

    @Test
    @WithMockUser(username = "admin", roles = { "SUPER_ADMIN" })
    void deleteDocument_ShouldReturnNoContent() throws Exception {
        String patientCode = "P12345";
        UUID documentId = UUID.randomUUID();

        doNothing().when(patientDocumentService).softDeleteDocument(eq(patientCode), eq(documentId), any());

        mockMvc.perform(delete("/api/v1/patients/{patientCode}/documents/{documentId}", patientCode, documentId)
                .with(csrf()))
                .andExpect(status().isNoContent());
    }
}
