# AdvSAC Autoscaler — Environment Stage

This folder currently implements the CloudSim environment and its plain baseline
reward/state representation. The actor, critics, replay buffer and training loop
are intentionally **not implemented yet**.

## Locked experiment settings

- RL decision interval: 10 simulated seconds
- Episode horizon: 1000 simulated seconds
- VM limits: minimum 1, initial 3, maximum 12
- State dimension: 8, all values normalized to [0, 1]
- Action values: {-2, -1, 0, +1, +2}
- SLO: 0.5 seconds
- Reward utilization term: average CPU utilization
- Constraint penalty: -1.0 if the VM count is outside the allowed range
- Random seed: 42
- Workload models: STABLE, PERIODIC, GRADUAL_INCREASE, SUDDEN_BURST

These are the project's initial experimental settings. The algorithmic
hyperparameters are informed by ASTRA, but the CloudSim-specific environment
settings are not presented as values copied from ASTRA.

## Select a workload

Set the Java system property before running:

- `-Dadvsac.workload=STABLE`
- `-Dadvsac.workload=PERIODIC`
- `-Dadvsac.workload=GRADUAL_INCREASE`
- `-Dadvsac.workload=SUDDEN_BURST`

If omitted, `STABLE` is selected.

## Current classes

- `AdvsacConfig`: fixed experiment settings and initial algorithm hyperparameters
- `AdvsacAction`: five discrete actions
- `AdvsacState`: validated 8-element normalized state
- `AdvsacStateBuilder`: raw CloudSim metrics to normalized state
- `AdvsacWorkloadGenerator`: internal CloudSim request generation
- `AdvsacRewardCalculator`: plain ASTRA-style reward, without risk terms
- `AdvsacEnvironment`: reset/step contract
- `AdvsacCloudSimEnvironment`: step-based CloudSim implementation
- `AdvsacTransition`: one state/action/reward/next-state transition
- `AdvsacEnvironmentSmokeTest`: no-RL integration test

## Next implementation stage

1. Run and debug the smoke test.
2. Verify all four workload profiles and state/reward metrics.
3. Add experiment metrics logging.
4. Only then add the plain discrete AdvSAC actor, critics, replay buffer and training loop.

Uncertainty estimation, distribution-shift detection, adversarial perturbation
and risk penalties remain out of scope for this stage.
