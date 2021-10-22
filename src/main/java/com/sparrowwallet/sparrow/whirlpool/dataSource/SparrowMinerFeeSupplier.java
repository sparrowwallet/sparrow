package com.sparrowwallet.sparrow.whirlpool.dataSource;

import com.samourai.wallet.api.backend.MinerFeeTarget;
import com.samourai.whirlpool.client.wallet.data.minerFee.MinerFeeSupplier;
import com.sparrowwallet.sparrow.AppServices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SparrowMinerFeeSupplier implements MinerFeeSupplier {
    private static final int FALLBACK_FEE_RATE = 75;

    public static SparrowMinerFeeSupplier instance;

    public static SparrowMinerFeeSupplier getInstance() {
        if (instance == null) {
            instance = new SparrowMinerFeeSupplier();
        }
        return instance;
    }

    private SparrowMinerFeeSupplier() {
    }

    @Override
    public int getFee(MinerFeeTarget feeTarget) {
        return getFee(Integer.parseInt(feeTarget.getValue()));
    }

    public static int getFee(int targetBlocks) {
        if(AppServices.getTargetBlockFeeRates() == null) {
            return FALLBACK_FEE_RATE;
        }

        return getMinimumFeeForTarget(targetBlocks);
    }

    private static Integer getMinimumFeeForTarget(int targetBlocks) {
        List<Map.Entry<Integer, Double>> feeRates = new ArrayList<>(AppServices.getTargetBlockFeeRates().entrySet());
        Collections.reverse(feeRates);
        for(Map.Entry<Integer, Double> feeRate : feeRates) {
            if(feeRate.getKey() <= targetBlocks) {
                return feeRate.getValue().intValue();
            }
        }

        return feeRates.get(0).getValue().intValue();
    }

}
