package pl.fhframework.core.model.dto.client;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import lombok.Data;

import java.util.Date;

/**
 * Message indicates that service with id "serviceId" is not supported (eg. browser doesn't support ceratin functions)
 */
@Data
@JsonSubTypes(
        @JsonSubTypes.Type(value = NotSupportedService.class, name = NotSupportedService.TYPE))
public class NotSupportedService extends AbstractClientMessage {
    static final String TYPE = "NoSupport";

    private Integer code; // specific to service

    private String reason;

    public NotSupportedService() {
        super(TYPE);
    }
}
