package com.delivery.service.delivery.config;

import lombok.Data;

@Data
public class BankEndpoint {

    private String bankCode;
    private String host;
    private int port;
}
