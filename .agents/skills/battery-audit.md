# Battery-audit workflow

1. Inventory long-lived components: services, workers, alarms, receivers, sensors, location, timers, callbacks, network sync, accessibility events.
2. Identify what wakes the app/process and at what cadence.
3. Replace polling with events where possible.
4. Narrow subscriptions and stop work when result cannot be consumed.
5. Remove unnecessary exact alarms/wake locks/periodic jobs.
6. Check screen-off behavior and hidden animation.
7. Measure controlled idle and navigation scenarios with batterystats/Perfetto.
8. Verify latency/product behavior did not regress.
9. Document any unavoidable long-lived work and platform requirement.
