package com.logistics.catalog.dto.request;

import com.logistics.catalog.dto.common.AttributeDefinitionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateCategoryRequest(

        @NotBlank(message = "name est obligatoire")
        @Size(min = 2, max = 80, message = "name doit comporter entre 2 et 80 caractères")
        String name,

        @NotBlank(message = "slug est obligatoire")
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "slug doit être composé de mots en minuscules séparés par un seul tiret")
        @Size(max = 60, message = "slug ne doit pas dépasser 60 caractères")
        String slug,

        /* Null means a root category. */
        String parentId,

        @Size(max = 30, message = "30 attributs au maximum par catégorie")
        List<@Valid AttributeDefinitionDto> attributeSchema) {
}
