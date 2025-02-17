package com.iraychev.expenseanalyzer.config.properties;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public record RsaKeyProperties (RSAPrivateKey privateKey, RSAPublicKey publicKey) {}
