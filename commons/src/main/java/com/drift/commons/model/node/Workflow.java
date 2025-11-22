package com.drift.commons.model.node;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.drift.commons.exception.ApiException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Workflow {
    private String id;
    private String comment;
    private String startNode;
    private String version;
    private String defaultFailureNode;
    private Map<String, WorkflowNode> states;
    private List<String> postWorkflowCompletionNodes;

    public void validateWFFields() {
        if (StringUtils.isEmpty(id)) {
            throw new ApiException(Response.Status.BAD_REQUEST, "id can't be empty");
        }
        if (StringUtils.isEmpty(startNode)) {
            throw new ApiException(Response.Status.BAD_REQUEST, "startNode can't be empty");
        }
        if (Objects.isNull(states) || states.isEmpty()) {
            throw new ApiException(Response.Status.BAD_REQUEST, "states can't be null or empty");
        }
    }
}