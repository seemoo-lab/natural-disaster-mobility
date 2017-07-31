# Natural Disaster Mobility Model and Scenarios

This repository contains a *mobility model for natural disasters* as well as two specific disaster scenarios: *2013 Typhoon Haiyan* (for the city of [Tacloban](https://www.openstreetmap.org/#map=18/11.24303/125.00720&layers=H)) and *2010 Haiti earthquake* (for the surroundings of [Port au Prince](https://www.openstreetmap.org/#map=18/18.54733/-72.34053&layers=H)).

*It also contains a number of non-upstream patches to the ONE, e.g., b15434a99da132f1376e80fd071a5f6f8316ed1a, 4f7fa115373a972883991a2aca285e2c82d784f0, 0707229877c471b7ef6c343b2cd2f7e9466fa8eb, b27d8313512d8fa98cee4f78794d730eb1aaf8eb.*

## Code Navigation

This repository is a fork of the [Opportunistic Network Environment (ONE) simulator](http://akeranen.github.io/the-one/) and, thus, we place our code and data in the following locations:

* `data/` contains map data and POI waypoints for the city of `Tacloban` and surroundings of `PortauPrince`
* `src/movement/` contains the implementations of the sub-mobility models
  * `NaturalDisasterMovementModel.java`: the main mobility model class schedules role-dependent activities 
  * `naturaldisaster/` contains the role-dependent activities (go to the airport, sleep, etc.)
* the base directory contains the scenario instantiations (`tacloban_settings.txt` and `portauprince_settings.txt`) in form of ONE settings files.

## Usage

To use the model, simply base your evaluation scenario on one of the scenario instantiations. If you plan to create your scenario from scratch, you need to define:
* `GroupX.role = {Healthy, Injured, Scientist, UN, Government, SnR, DRO}` for each group. This setting is independent from `GroupX.groupID`, so you can have different groups with the same role.
* `Group.nbrOfDays` as the number of days that the scenario will be run for (in a future version of this model, this could be derived from the `Scenario.endTime` setting)

## Publications

For more information on the underlying model, please refer to our paper. If you use this model or code in your own work, we are happy to receive a citation.

Milan Schmittner, Max Maass, Tom Schons, and Matthias Hollick, “**Reverse Engineering Human Mobility in Large-scale Natural Disasters**,” in *ACM International Conference on Modeling, Analysis and Simulation of Wireless and Mobile Systems (MSWiM)*, November 2017, Miami Beach, USA.

## Reproducibility

For the sake of reproducibility, we provide the complete experimental data set that was used for the plots in our paper (requires Python packages `numpy` and `matplotlib`) at [10.5281/zenodo.836815](https://doi.org/10.5281/zenodo.836815).

Alternatively, you may generate the data set yourself by running (**warning**: simulations might take several days to complete):
```
./exp.py
```
After completion, experiment data and plots will be available in `out/<DATE>/{reports,plots}`.
