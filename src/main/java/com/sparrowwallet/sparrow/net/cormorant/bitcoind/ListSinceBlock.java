package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ListSinceBlock(List<ListTransaction> transactions, List<ListTransaction> removed, String lastblock) {

}
