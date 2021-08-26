package com.sparrowwallet.sparrow.whirlpool.dataSource;

import com.samourai.wallet.api.backend.MinerFee;
import com.samourai.wallet.api.backend.MinerFeeTarget;
import com.samourai.whirlpool.client.wallet.data.minerFee.MinerFeeSupplier;
import com.sparrowwallet.sparrow.AppServices;

import java.util.*;

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
        if (AppServices.getTargetBlockFeeRates() == null) {
            return FALLBACK_FEE_RATE;
        }
        return getMinimumFeeForTarget(Integer.parseInt(feeTarget.getValue()));
    }

    @Override
    public MinerFee getValue() {
        Map<String, Integer> fees = new LinkedHashMap<>();
        for (MinerFeeTarget minerFeeTarget : MinerFeeTarget.values()) {
            fees.put(minerFeeTarget.getValue(), getFee(minerFeeTarget));
        }
        return new MinerFee(fees);
    }

    private Integer getMinimumFeeForTarget(int targetBlocks) {
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
