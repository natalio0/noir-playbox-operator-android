NOIR PLAYBOX — COMPOSE WEIGHT FIX V1
====================================

Error:
Cannot access 'val RowColumnParentData?.weight: Float': it is internal in file.

Cause:
DeviceDetailScreen.kt explicitly imported:

    import androidx.compose.foundation.layout.weight

With the Compose/Kotlin versions in this project, that import can resolve to an
internal implementation property instead of the RowScope/ColumnScope weight API.

Fix:
Remove the explicit import. Calls like:

    Modifier.weight(1f)

inside a Row/Column scope continue to resolve correctly.

Apply:

    cd /Users/hazel/AndroidStudioProjects/NoirPlayboxOperatorStarterV1
    unzip -o ~/Downloads/noir-playbox-compose-weight-fix-v1.zip -d .
    ./scripts/apply-compose-weight-fix-v1.sh
    ./scripts/dev-run.sh

The "Unable to strip ... .so" message is a packaging warning, not the cause of
the Kotlin compile failure.
