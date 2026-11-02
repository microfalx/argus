package net.microfalx.argus.spring;

import net.microfalx.argus.api.Failure.Type;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentConversionNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.*;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static net.microfalx.argus.api.Failure.registerType;

class InitializeWeb {

    void initialize() {
        registerFailureTypes();
    }

    private void registerFailureTypes() {
        registerType(Type.NETWORK, ResourceAccessException.class);

        registerType(Type.TIMED_OUT, AsyncRequestTimeoutException.class);
        registerType(Type.TIMED_OUT, HttpServerErrorException.GatewayTimeout.class);

        registerType(Type.AUTHENTICATION, HttpClientErrorException.Unauthorized.class);
        registerType(Type.AUTHORIZATION, HttpClientErrorException.Forbidden.class);

        registerType(Type.RESOURCE_NOT_FOUND, NoHandlerFoundException.class);
        registerType(Type.RESOURCE_NOT_FOUND, NoResourceFoundException.class);
        registerType(Type.RESOURCE_NOT_FOUND, HttpClientErrorException.NotFound.class);
        registerType(Type.RESOURCE_NOT_FOUND, HttpClientErrorException.Gone.class);

        registerType(Type.CONFLICT, HttpClientErrorException.Conflict.class);

        registerType(Type.OVERLOAD, HttpClientErrorException.TooManyRequests.class);

        registerType(Type.SERVICE_UNAVAILABLE, HttpServerErrorException.ServiceUnavailable.class);
        registerType(Type.SERVICE_UNAVAILABLE, HttpServerErrorException.BadGateway.class);

        registerType(Type.ABORT, AsyncRequestNotUsableException.class);

        registerType(Type.ILLEGAL_INPUT, HttpClientErrorException.BadRequest.class);
        registerType(Type.ILLEGAL_INPUT, HttpClientErrorException.MethodNotAllowed.class);
        registerType(Type.ILLEGAL_INPUT, HttpClientErrorException.NotAcceptable.class);
        registerType(Type.ILLEGAL_INPUT, HttpClientErrorException.UnsupportedMediaType.class);
        registerType(Type.ILLEGAL_INPUT, HttpClientErrorException.UnprocessableEntity.class);
        registerType(Type.ILLEGAL_INPUT, HttpMessageNotReadableException.class);
        registerType(Type.ILLEGAL_INPUT, HttpMediaTypeNotSupportedException.class);
        registerType(Type.ILLEGAL_INPUT, HttpMediaTypeNotAcceptableException.class);
        registerType(Type.ILLEGAL_INPUT, HttpRequestMethodNotSupportedException.class);
        registerType(Type.ILLEGAL_INPUT, InvalidMediaTypeException.class);
        registerType(Type.ILLEGAL_INPUT, ServletRequestBindingException.class);
        registerType(Type.ILLEGAL_INPUT, MissingServletRequestParameterException.class);
        registerType(Type.ILLEGAL_INPUT, MissingRequestHeaderException.class);
        registerType(Type.ILLEGAL_INPUT, MissingRequestCookieException.class);
        registerType(Type.ILLEGAL_INPUT, MissingMatrixVariableException.class);
        registerType(Type.ILLEGAL_INPUT, MissingServletRequestPartException.class);
        registerType(Type.ILLEGAL_INPUT, UnsatisfiedServletRequestParameterException.class);
        registerType(Type.ILLEGAL_INPUT, MethodArgumentNotValidException.class);
        registerType(Type.ILLEGAL_INPUT, MethodArgumentTypeMismatchException.class);
        registerType(Type.ILLEGAL_INPUT, HandlerMethodValidationException.class);
        registerType(Type.ILLEGAL_INPUT, MultipartException.class);
        registerType(Type.ILLEGAL_INPUT, MaxUploadSizeExceededException.class);
        registerType(Type.ILLEGAL_INPUT, ServerWebInputException.class);
        registerType(Type.ILLEGAL_INPUT, MethodNotAllowedException.class);
        registerType(Type.ILLEGAL_INPUT, NotAcceptableStatusException.class);
        registerType(Type.ILLEGAL_INPUT, UnsupportedMediaTypeStatusException.class);
        registerType(Type.ILLEGAL_INPUT, PayloadTooLargeException.class);

        registerType(Type.ILLEGAL_OUTPUT, HttpMessageNotWritableException.class);

        registerType(Type.CONFIGURATION, MissingPathVariableException.class);
        registerType(Type.CONFIGURATION, MethodArgumentConversionNotSupportedException.class);
        registerType(Type.CONFIGURATION, HttpServerErrorException.NotImplemented.class);
    }
}
