package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.arteam.simplejsonrpc.core.domain.ErrorMessage;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ImportDescriptorResult(boolean success, List<String> warnings, ErrorMessage error) {
}
