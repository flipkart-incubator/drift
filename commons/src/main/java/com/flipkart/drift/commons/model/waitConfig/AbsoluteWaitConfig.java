package com.flipkart.drift.commons.model.waitConfig;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flipkart.drift.commons.model.enums.WaitType;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.FutureOrPresent;
import javax.validation.constraints.NotNull;
import java.util.Date;


@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AbsoluteWaitConfig extends WaitConfig {
    @NotNull(message = "timestamp must be provided")
    @FutureOrPresent
    @JsonFormat(shape = JsonFormat.Shape.STRING,
            pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'",
            timezone = "UTC")
    private Date timestamp;

    public AbsoluteWaitConfig() {
        super(WaitType.ABSOLUTE_WAIT);
    }

}
