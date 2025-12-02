package com.flipkart.drift.sdk.model.instruction;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Instruction {
    private String id;
    private String message;
}
