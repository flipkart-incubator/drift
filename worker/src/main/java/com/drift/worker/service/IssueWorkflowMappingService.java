package com.drift.worker.service;

import com.drift.commons.exception.ApiException;
import com.drift.worker.model.ABDetails;
import com.drift.worker.model.IssueWorkflowMapping;
import com.drift.worker.Utility.ABHelper;
import com.flipkart.kloud.config.ConfigClient;
import com.flipkart.kloud.config.DynamicBucket;
import com.drift.commons.model.client.request.WorkflowStartRequest;
import com.flipkart.kloud.config.error.ConfigServiceException;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.core.Response;
import java.util.*;

@Slf4j
public record IssueWorkflowMappingService(ConfigClient configClient, ABHelper abHelper, String bucketName) {
    private static final String ISSUE_WORKFLOW_MAPPING_KEY = "issue_workflow_mapping";
    private static final String WORKFLOW_ID_KEY = "workflowId";
    private static final String AB_ENABLED_KEY = "abEnabled";
    private static final String AB_META_KEY = "abMeta";
    private static final String DEFAULT_VERSION_KEY = "version";
    private static final String EXPERIMENT_NAME_KEY = "experimentName";
    private static final String PIVOT_TYPE_KEY = "pivotType";
    private static final String VARIABLE_KEY = "variable";
    private static final String CONTROL_KEY = "control";
    private static final String TREATMENT_KEY = "treatment";
    private static final String VERSION_KEY = "version";
    public static final String PIVOT_TYPE_CUSTOMER_ID = "customerId";

    @Inject
    public IssueWorkflowMappingService(ConfigClient configClient, ABHelper abHelper, @Named("hbaseConfigBucket") String bucketName) {
        this.configClient = configClient;
        this.abHelper = abHelper;
        this.bucketName = bucketName;
    }

    public List<Map<String, Object>> getIssueWorkflowMapping() {
        try {
            DynamicBucket bucket = getBucket();
            return extractIssueWorkflowMapping(bucket);
        } catch (ConfigServiceException e) {
            log.error("Failed to fetch issue workflow mapping from bucket: {}", bucketName, e);
            throw new ApiException(Response.Status.INTERNAL_SERVER_ERROR, "Failed to fetch issue workflow mapping");
        }
    }

    public IssueWorkflowMapping getIssueWorkflowMappingForIssue(String issueId) {
        if (issueId == null || issueId.trim().isEmpty()) {
            log.debug("Issue ID is null or empty");
            return null;
        }
        try {
            List<Map<String, Object>> issueWorkflowMapping = getIssueWorkflowMapping();
            if (issueWorkflowMapping.isEmpty()) {
                return null;
            }
            Map<String, Object> mappingEntry = issueWorkflowMapping.get(0);
            if (!mappingEntry.containsKey(issueId)) {
                log.info("No workflow mapping found for issue ID: {}", issueId);
                return null;
            }
            Map<String, Object> issueConfig = (Map<String, Object>) mappingEntry.get(issueId);
            return extractIssueWorkflowMappingFromConfig(issueId, issueConfig);
        } catch (Exception e) {
            log.error("Error extracting workflow mapping for issue ID: {}", issueId, e);
            return null;
        }
    }


    public Map<String, String> getABRequestMapping(WorkflowStartRequest workflowStartRequest, IssueWorkflowMapping mapping) {
        String issueId = workflowStartRequest.getIssueDetail().getIssueId();
        if (mapping == null || !mapping.hasValidABConfig()) {
            log.debug("Invalid AB mapping for issue: {}, mapping: {}", issueId, mapping);
            return null;
        }
        Map<String, String> result = new HashMap<>();
        try {
            String version;
            boolean isInTreatment = false;
            ABDetails abDetails = mapping.getAbMetaDetails();
            if (abDetails.getPivotType().equals(PIVOT_TYPE_CUSTOMER_ID)) {
                isInTreatment = abHelper.isCustomerInTreatment(
                        workflowStartRequest.getCustomer().getCustomerId(),
                        abDetails.getExperimentName(),
                        abDetails.getVariable()
                );
            }
            version = isInTreatment ? abDetails.getTreatment().getVersion() : abDetails.getControl().getVersion();
            if (version == null) {
                log.debug("Version is null for issue: {}, isInTreatment: {} , so picking default version", issueId, isInTreatment);
                version = mapping.getDefaultVersion();
            }
            log.info("AB mapping for issue {}: workflowId={}, version={}, bucket={}",
                    issueId, mapping.getWorkflowId(), version, (isInTreatment ? "TREATMENT" : "CONTROL"));
            result.put(WORKFLOW_ID_KEY, mapping.getWorkflowId());
            result.put(VERSION_KEY, version);

        } catch (Exception e) {
            log.error("Error getting AB request mapping for issue: {}", issueId, e);
        }
        return result;
    }


    private DynamicBucket getBucket() throws ConfigServiceException {
        return configClient.getDynamicBucket(bucketName);
    }

    private List<Map<String, Object>> extractIssueWorkflowMapping(DynamicBucket bucket) {
        if (!bucket.getKeys().containsKey(ISSUE_WORKFLOW_MAPPING_KEY)) {
            log.warn("Issue workflow mapping key not found in bucket: {}", bucketName);
            return new ArrayList<>();
        }

        try {
            List<Map<String, Object>> result = (List<Map<String, Object>>) bucket.getKeys().get(ISSUE_WORKFLOW_MAPPING_KEY);
            if (result == null) {
                log.warn("Null result from bucket for key: {}", ISSUE_WORKFLOW_MAPPING_KEY);
                return new ArrayList<>();
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to get AB workflow config from bucket: {}", bucketName, e);
            return new ArrayList<>();
        }
    }

    private IssueWorkflowMapping extractIssueWorkflowMappingFromConfig(String issueId, Map<String, Object> issueConfig) {
        String workflowId = (String) issueConfig.get(WORKFLOW_ID_KEY);
        String defaultVersion = (String) issueConfig.get(DEFAULT_VERSION_KEY);
        Boolean abEnabled = (Boolean) issueConfig.get(AB_ENABLED_KEY);

        ABDetails abMetaDetails = null;
        if (abEnabled != null && abEnabled) {
            abMetaDetails = extractABDetailsFromConfig(issueId, issueConfig);
        }

        return IssueWorkflowMapping.builder()
                .workflowId(workflowId)
                .defaultVersion(defaultVersion)
                .abEnabled(abEnabled != null && abEnabled)
                .abMetaDetails(abMetaDetails)
                .build();
    }

    private ABDetails extractABDetailsFromConfig(String issueId, Map<String, Object> issueConfig) {
        String workflowId = (String) issueConfig.get(WORKFLOW_ID_KEY);
        Map<String, Object> abMeta = (Map<String, Object>) issueConfig.get(AB_META_KEY);
        if (abMeta == null) {
            return null;
        }

        String experimentName = (String) abMeta.get(EXPERIMENT_NAME_KEY);
        String pivotType = (String) abMeta.get(PIVOT_TYPE_KEY);
        String variable = (String) abMeta.get(VARIABLE_KEY);

        if (experimentName == null || pivotType == null || variable == null) {
            log.error("Missing experiment name, pivot type, or variable for issue ID: {}", issueId);
            return null;
        }

        Map<String, Object> controlMap = (Map<String, Object>) abMeta.get(CONTROL_KEY);
        ABDetails.ABConfig control = null;
        if (controlMap != null) {
            control = ABDetails.ABConfig.builder()
                    .version((String) controlMap.get(VERSION_KEY))
                    .build();
        }

        Map<String, Object> treatmentMap = (Map<String, Object>) abMeta.get(TREATMENT_KEY);
        ABDetails.ABConfig treatment = null;
        if (treatmentMap != null) {
            treatment = ABDetails.ABConfig.builder()
                    .version((String) treatmentMap.get(VERSION_KEY))
                    .build();
        }

        return ABDetails.builder()
                .issueId(issueId)
                .workflowId(workflowId)
                .experimentName(experimentName)
                .pivotType(pivotType)
                .variable(variable)
                .control(control)
                .treatment(treatment)
                .build();
    }
}