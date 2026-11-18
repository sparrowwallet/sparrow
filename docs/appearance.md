# Desktop appearance

Choose **Classic** or **Modern** in the status bar or in **View → Appearance**. The `appearance` preference is shared by the desktop windows. Classic is the fallback for older profiles and unfamiliar values. **View → Theme** still selects Light, Dark or System independently.

Modern is a local JavaFX stylesheet added to scene roots. The switch updates the existing scene graph in place. Scenes at or below 850 points wide receive the compact Modern class, which resizing updates on the same root. Keep appearance rules scoped to the Modern root classes in `modern.css`, and preserve the original stylesheet order, control identities, text bindings, QR image geometry and semantic status indicators.

Wallet tables use the existing constrained resize policy. An automatic column fit caused by a same-size appearance switch is temporary. Explicit column and window resizing still use the existing column preference behavior. When adding a visual rule, verify both appearances on the same scene and fixture, including focus, selected and disabled states.

## Local verification

Use the pinned submodules and the [release build instructions](reproducible.md).

```sh
./gradlew test
./gradlew jpackage
```

Use a fresh, owner-only directory for each application instance. Keep the path short, absolute and free of spaces. Use only synthetic regtest wallets and disconnect any personal signing devices. Run the application in an isolated environment with IP connections blocked. Confirm the effective network is regtest and the application is offline before loading any fixtures.

```sh
qa_dir="$(mktemp -d /tmp/sparrow-ui.XXXXXXXX)"
umask 077
mkdir "$qa_dir/regtest"
printf '%s\n' '{"mode":"OFFLINE"}' > "$qa_dir/regtest/config"
SPARROW_NO_LOCK_FILE_LINK=true ./sparrow --dir "$qa_dir" --network regtest
```

Compare the upstream Classic, current Classic and Modern views on the same synthetic wallet at 650 × 708 and 1070 × 770. Check Light, Dark and a system theme change, switch from both controls, and restart to verify persistence. Switch while a dialog and a Send draft are open and verify selections, values and serialized unsigned PSBT bytes. Check QR decoding, copied values, masked amounts, keyboard focus and dialog confirmation and dismissal. Keep synthetic profiles and screenshots outside the repository.
