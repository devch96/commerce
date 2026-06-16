package com.sparta.copa.copapayment.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  private final boolean success;
  private final T data;
  private final String error;

  private ApiResponse(boolean success, T data, String error) {
    this.success = success;
    this.data = data;
    this.error = error;
  }

  public static ApiResponse<Void> success() {
    return new ApiResponse<>(true, null, null);
  }

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, data, null);
  }

  public static <T> ApiResponse<T> error(String message) {
    return new ApiResponse<>(false, null, message);
  }
}
