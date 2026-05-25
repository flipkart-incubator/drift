package com.flipkart.drift.worker.activities;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.flipkart.drift.commons.model.clientComponent.HttpComponents;
import com.flipkart.drift.commons.model.clientComponent.TransformerComponents;
import com.flipkart.drift.commons.model.enums.HttpContentTypeEnum;
import com.flipkart.drift.commons.model.enums.HttpMethod;
import com.flipkart.drift.commons.model.node.HttpNode;
import com.flipkart.drift.commons.model.resolvedDetails.HttpDetails;
import com.flipkart.drift.commons.model.resolvedDetails.TransformerDetails;
import com.flipkart.drift.sdk.model.enums.WorkflowStatus;
import com.flipkart.drift.worker.exception.HttpRetryableException;
import com.flipkart.drift.worker.executor.HttpExecutor;
import com.flipkart.drift.worker.model.activity.ActivityRequest;
import com.flipkart.drift.worker.model.activity.ActivityResponse;
import com.flipkart.drift.worker.service.WorkflowConfigStoreService;
import com.flipkart.drift.worker.service.WorkflowContextHBService;
import com.flipkart.drift.worker.translator.ClientResolvedDetailBuilder;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HttpNodeNodeActivityImpl#executeNode(ActivityRequest)}.
 *
 * <p>Tests the Harmony-parity exception routing:
 * <ul>
 *   <li>5xx / 408 / 429 ({@link HttpRetryableException}) → activity throws retryable
 *       {@link ApplicationFailure} so Temporal retries the activity</li>
 *   <li>4xx non-retryable ({@link IOException}) → activity returns
 *       {@link WorkflowStatus#FAILED} to route workflow to failure node</li>
 *   <li>2xx → activity returns {@link WorkflowStatus#RUNNING}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class HttpNodeNodeActivityImplTest {

    @Mock
    private WorkflowContextHBService workflowContextHBService;

    @Mock
    private WorkflowConfigStoreService workflowConfigStoreService;

    @Mock
    private HttpExecutor httpExecutor;

    private HttpNodeNodeActivityImpl activity;

    @BeforeEach
    void setUp() {
        activity = new HttpNodeNodeActivityImpl(workflowContextHBService, workflowConfigStoreService);
    }

    // ---- Helper: build a minimal ActivityRequest<HttpNode> ----

    private ActivityRequest<HttpNode> buildRequest() {
        HttpNode httpNode = new HttpNode();
        httpNode.setId("test-node-id");
        httpNode.setHttpComponents(new HttpComponents());
        httpNode.setTransformerComponents(new TransformerComponents());

        ActivityRequest<HttpNode> request = new ActivityRequest<>();
        request.setNodeDefinition(httpNode);
        request.setContext(JsonNodeFactory.instance.objectNode());
        request.setThreadContext(Collections.emptyMap());
        request.setWorkflowId("test-workflow-id");
        return request;
    }

    // ---- Test: 5xx path (HttpRetryableException) ----

    @Test
    void retryableHttpError_throwsApplicationFailureWithRetryableType() throws IOException {
        ActivityRequest<HttpNode> request = buildRequest();

        HttpDetails stubHttpDetails = HttpDetails.builder()
                .url("http://example.com/api")
                .method(HttpMethod.GET)
                .contentType(HttpContentTypeEnum.APPLICATION_JSON)
                .headers(new HashMap<>())
                .queryParams(Collections.emptyMap())
                .build();

        try (MockedStatic<ClientResolvedDetailBuilder> mockedBuilder = mockStatic(ClientResolvedDetailBuilder.class);
             MockedStatic<HttpExecutor> mockedExecutor = mockStatic(HttpExecutor.class)) {

            mockedBuilder.when(() -> ClientResolvedDetailBuilder.evaluateGroovy(
                    any(HttpComponents.class), any(), any(), eq(HttpDetails.class)))
                    .thenReturn(stubHttpDetails);

            mockedExecutor.when(() -> HttpExecutor.getInstance(anyString()))
                    .thenReturn(httpExecutor);
            when(httpExecutor.execute(any(HttpDetails.class), anyString()))
                    .thenThrow(new HttpRetryableException("HTTP retryable error: 503"));
            when(workflowConfigStoreService.getEnumMapping())
                    .thenReturn(Collections.emptyMap());

            ApplicationFailure thrown = assertThrows(ApplicationFailure.class,
                    () -> activity.executeNode(request));

            assertEquals("HTTP_RETRYABLE", thrown.getType());
            assertTrue(thrown.getMessage().contains("503") || thrown.getMessage().contains("retryable"),
                    "ApplicationFailure message should reference retryable error");
            assertFalse(thrown.isNonRetryable(),
                    "5xx ApplicationFailure must be retryable so Temporal retries the activity");
        }
    }

    // ---- Test: 4xx non-retryable path (IOException) ----

    @Test
    void nonRetryableHttpError_returnsFAILEDStatus() throws IOException {
        ActivityRequest<HttpNode> request = buildRequest();

        HttpDetails stubHttpDetails = HttpDetails.builder()
                .url("http://example.com/api")
                .method(HttpMethod.GET)
                .contentType(HttpContentTypeEnum.APPLICATION_JSON)
                .headers(new HashMap<>())
                .queryParams(Collections.emptyMap())
                .build();

        try (MockedStatic<ClientResolvedDetailBuilder> mockedBuilder = mockStatic(ClientResolvedDetailBuilder.class);
             MockedStatic<HttpExecutor> mockedExecutor = mockStatic(HttpExecutor.class)) {

            mockedBuilder.when(() -> ClientResolvedDetailBuilder.evaluateGroovy(
                    any(HttpComponents.class), any(), any(), eq(HttpDetails.class)))
                    .thenReturn(stubHttpDetails);

            mockedExecutor.when(() -> HttpExecutor.getInstance(anyString()))
                    .thenReturn(httpExecutor);
            when(httpExecutor.execute(any(HttpDetails.class), anyString()))
                    .thenThrow(new IOException("HTTP non-retryable error: 404"));
            when(workflowConfigStoreService.getEnumMapping())
                    .thenReturn(Collections.emptyMap());

            ActivityResponse response = activity.executeNode(request);

            assertNotNull(response);
            assertEquals(WorkflowStatus.FAILED, response.getWorkflowStatus(),
                    "4xx non-retryable errors must return FAILED to route to failure node");
        }
    }

    // ---- Test: 408 path (HttpRetryableException) ----

    @Test
    void requestTimeout408_throwsRetryableApplicationFailure() throws IOException {
        ActivityRequest<HttpNode> request = buildRequest();

        HttpDetails stubHttpDetails = HttpDetails.builder()
                .url("http://example.com/api")
                .method(HttpMethod.POST)
                .contentType(HttpContentTypeEnum.APPLICATION_JSON)
                .headers(new HashMap<>())
                .queryParams(Collections.emptyMap())
                .body(Collections.emptyMap())
                .build();

        try (MockedStatic<ClientResolvedDetailBuilder> mockedBuilder = mockStatic(ClientResolvedDetailBuilder.class);
             MockedStatic<HttpExecutor> mockedExecutor = mockStatic(HttpExecutor.class)) {

            mockedBuilder.when(() -> ClientResolvedDetailBuilder.evaluateGroovy(
                    any(HttpComponents.class), any(), any(), eq(HttpDetails.class)))
                    .thenReturn(stubHttpDetails);

            mockedExecutor.when(() -> HttpExecutor.getInstance(anyString()))
                    .thenReturn(httpExecutor);
            when(httpExecutor.execute(any(HttpDetails.class), anyString()))
                    .thenThrow(new HttpRetryableException("HTTP retryable error: 408"));
            when(workflowConfigStoreService.getEnumMapping())
                    .thenReturn(Collections.emptyMap());

            ApplicationFailure thrown = assertThrows(ApplicationFailure.class,
                    () -> activity.executeNode(request));

            assertEquals("HTTP_RETRYABLE", thrown.getType());
            assertFalse(thrown.isNonRetryable());
        }
    }

    // ---- Test: 429 path (HttpRetryableException) ----

    @Test
    void tooManyRequests429_throwsRetryableApplicationFailure() throws IOException {
        ActivityRequest<HttpNode> request = buildRequest();

        HttpDetails stubHttpDetails = HttpDetails.builder()
                .url("http://example.com/api")
                .method(HttpMethod.GET)
                .contentType(HttpContentTypeEnum.APPLICATION_JSON)
                .headers(new HashMap<>())
                .queryParams(Collections.emptyMap())
                .build();

        try (MockedStatic<ClientResolvedDetailBuilder> mockedBuilder = mockStatic(ClientResolvedDetailBuilder.class);
             MockedStatic<HttpExecutor> mockedExecutor = mockStatic(HttpExecutor.class)) {

            mockedBuilder.when(() -> ClientResolvedDetailBuilder.evaluateGroovy(
                    any(HttpComponents.class), any(), any(), eq(HttpDetails.class)))
                    .thenReturn(stubHttpDetails);

            mockedExecutor.when(() -> HttpExecutor.getInstance(anyString()))
                    .thenReturn(httpExecutor);
            when(httpExecutor.execute(any(HttpDetails.class), anyString()))
                    .thenThrow(new HttpRetryableException("HTTP retryable error: 429"));
            when(workflowConfigStoreService.getEnumMapping())
                    .thenReturn(Collections.emptyMap());

            ApplicationFailure thrown = assertThrows(ApplicationFailure.class,
                    () -> activity.executeNode(request));

            assertEquals("HTTP_RETRYABLE", thrown.getType());
            assertFalse(thrown.isNonRetryable());
        }
    }
}
