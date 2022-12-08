package com.sparrowwallet.sparrow.net.cormorant.bitcoind;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ListTransaction(String address, List<String> parent_descs, Category category, double amount, int vout, double fee, int confirmations, String blockhash, int blockindex, long blocktime, int blockheight, String txid, long time, long timereceived, List<String> walletconflicts) {
}
