alter table wallet add column birthHeight integer after birthDate;
alter table walletNode add column silentPaymentTweak varbinary(32) after addressData;
alter table keystore add column silentPaymentScanAddress varbinary(65) after externalPaymentCode;