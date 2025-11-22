package com.drift.commons.model.clientComponent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.drift.commons.model.componentDetail.ComponentDetail;
import com.drift.commons.model.value.MapValue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

@Data
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
@EqualsAndHashCode
public abstract class ClientComponents {
    @JsonIgnore
    private ComponentDetail<MapValue, Map> scriptedFieldsInfoMap;

    protected abstract String getComponentSpecificHashInput();

    public String computeUniqueComponentHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Add component-specific fields by calling the abstract method
            String componentSpecificInput = getComponentSpecificHashInput();
            if (componentSpecificInput != null) {
                digest.update(componentSpecificInput.getBytes());
            }

            // Convert to hex string
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate component hash", e);
        }
    }
}
