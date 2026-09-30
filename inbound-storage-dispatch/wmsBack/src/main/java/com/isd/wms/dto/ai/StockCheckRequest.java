package com.isd.wms.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record StockCheckRequest(
    @JsonProperty(required = true)
    @JsonPropertyDescription("The barcode of the product to search for in the warehouse")
    String barcode
) {}
