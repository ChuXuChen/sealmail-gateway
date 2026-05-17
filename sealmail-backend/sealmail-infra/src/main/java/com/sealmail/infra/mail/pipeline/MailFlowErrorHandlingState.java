package com.sealmail.infra.mail.pipeline;

import org.springframework.stereotype.Component;

@Component
public class MailFlowErrorHandlingState {

    private final ThreadLocal<Result> result = new ThreadLocal<>();

    public void clear() {
        result.remove();
    }

    public void markHandled() {
        result.set(Result.handledResult());
    }

    public void markFailed(Throwable failure) {
        result.set(Result.failed(failure));
    }

    public Result take() {
        Result current = result.get();
        result.remove();
        return current;
    }

    public record Result(boolean handled, Throwable failure) {
        static Result handledResult() {
            return new Result(true, null);
        }

        static Result failed(Throwable failure) {
            return new Result(false, failure);
        }
    }
}
