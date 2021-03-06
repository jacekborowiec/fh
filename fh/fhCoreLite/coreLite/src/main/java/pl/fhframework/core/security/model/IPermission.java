package pl.fhframework.core.security.model;

import java.time.LocalDate;

/**
 * Interface representing a permission (association between business role and system function).
 * @author tomasz.kozlowski (created on 2017-11-22)
 */
public interface IPermission {

    /** Gets a permission identifier */
    Long getId();

    /** Gets associated business role name */
    String getBusinessRoleName();

    /** Gets associated system function name */
    String getFunctionName();

    /** Gets module UUID which provides system function */
    String getModuleUUID();

    /** Gets creation date */
    LocalDate getCreationDate();
    /** Sets creation date */
    void setCreationDate(LocalDate creationDate);

    /** Gets name of a permission creator */
    String getCreatedBy();
    /** Sets name of a permission creator */
    void setCreatedBy(String createdBy);

    /** Gets whether it is a permission denial */
    Boolean getDenial();
    /** Sets whether it is a permission denial */
    void setDenial(Boolean denial);
    /** Gets whether it is a permission denial */
    default boolean isDenied() {
        return getDenial() != null ? getDenial() : false;
    }

}
