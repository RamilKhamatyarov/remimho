# Combo naming and protobuf mapping boundary

The domain value is named `Combo`, the state property is `combo`, and its schema is `combo.proto` with a `Combo` message. `ComboMapper.kt` and `GameStateMapper.kt` expose the existing extension functions from `ru.rkhamatyarov.mapping.proto`, keeping protobuf conversion outside the service package. An import alias distinguishes the protobuf `Combo` from the domain value.

Field numbers and wire types remain unchanged so binary replay and history data remain readable. Protobuf field names change from `combo_config` to `combo`, which changes generated accessors and protobuf JSON names. Deployment properties retain the `remimho.combos.*` prefix and the `ComboSettings` class because these describe server settings captured at room creation.
