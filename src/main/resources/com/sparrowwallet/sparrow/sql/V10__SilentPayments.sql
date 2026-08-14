alter table wallet add column birthHeight integer after birthDate;
alter table walletNode add column silentPaymentTweak varbinary(32) after addressData;
alter table keystore add column silentPaymentScanAddress varbinary(65) after externalPaymentCode;
create table silentPaymentAddress (address varbinary(32) primary key not null, silentPaymentAddress varbinary(67) not null);