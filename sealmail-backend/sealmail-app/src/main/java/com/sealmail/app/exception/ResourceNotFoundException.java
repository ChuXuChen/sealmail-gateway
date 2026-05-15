package com.sealmail.app.exception;

public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String resourceType, String id) {
        super("RESOURCE_NOT_FOUND", String.format("%s not found: %s", resourceType, id));
    }

    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message);
    }

    public static ResourceNotFoundException notFound(String resourceType, String id) {
        return new ResourceNotFoundException(resourceType, id);
    }
}
