package com.commitgen.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CommitRequest {

    @NotBlank(message = "diff is required")
    @Size(max = 10000, message = "diff must be under 10000 characters")
    private String diff;

    private String language = "en";

    private String style = "conventional";

    @Min(value = 1, message = "minimum 1 suggestion")
    @Max(value = 5, message = "max 5 suggestions")
    private Integer quantity = 3;
}
