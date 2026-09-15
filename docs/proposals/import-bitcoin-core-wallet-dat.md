# Support importing Bitcoin Core wallet.dat files

## Summary
EntropyLab currently exports a ready-to-load Bitcoin Core `wallet.dat` for single-signature watch-only wallets, and a multisig version is about to merge. It would be great if Sparrow could accept that same file directly, so users don't have to manually copy descriptors.

Sparrow already parses output descriptors, so the work is mostly reading the Core SQLite database format (or the legacy Berkeley DB) and feeding the descriptors inside into the existing importer. This covers both single-sig and the upcoming multisig watch-only files without touching private keys.

## Motivation
- EntropyLab (and similar air-gapped tools) produce a Core-compatible `wallet.dat` with descriptors already imported.
- Users currently have to extract the descriptor text by hand and paste it into Sparrow.
- A direct import would make the EntropyLab → Sparrow path as smooth as EntropyLab → Bitcoin Core.

## Implementation notes
- Parse the SQLite `wallet.dat` (modern Core descriptor wallets) for `desc` entries in the `main` table or equivalent.
- Fall back to legacy formats if needed.
- Reuse the existing descriptor import path so multisig, script types, and ranges are handled correctly.
- No private key material should be imported or stored; watch-only only.

## Test plan
- [ ] Generate a single-sig watch-only `wallet.dat` from EntropyLab and import it into Sparrow.
- [ ] Generate a multisig watch-only `wallet.dat` (once the EntropyLab PR lands) and import it.
- [ ] Verify addresses, balance, and transaction history match Bitcoin Core.
- [ ] Confirm no private keys are exposed or stored in the Sparrow wallet file.
