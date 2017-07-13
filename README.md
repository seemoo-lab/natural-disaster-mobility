# Natural Disaster Mobility Model and Scenarios

This repository contains a *mobility model for natural disasters* as well as two specific disaster scenarios: *2013 Typhoon Haiyan* (for the city of [Tacloban](https://www.openstreetmap.org/#map=18/11.24303/125.00720&layers=H)) and *2010 Haiti earthquake* (for the surroundings of [Port au Prince](https://www.openstreetmap.org/#map=18/18.54733/-72.34053&layers=H)).

## Code Navigation

This repository is a fork of the [Opportunistic Network Environment (ONE) simulator](http://akeranen.github.io/the-one/) and, thus, we place our code and data in the following locations:

* `data/` contains map data and POI waypoints for the city of `Tacloban` and surroundings of `PortauPrince`
* `src/movement/` contains the implementations of the sub-mobility models
  * `NaturalDisasterMovementModel.java`: the main mobility model class schedules role-dependent activities 
  * `naturaldisaster/` contains the role-dependent activities (go to the airport, sleep, etc.)
* `.` contains the scenario instantiations (`tacloban_settings.txt` and `portauprince_settings.txt`)

## Usage

To use the model, simply base your evaluation scenario on one of the scenario instantiations. If you plan to create your scenario from scratch, you need to define ...

* `GroupX.role = {Healthy, Injured, Scientist, UN, Government, SnR, DRO}` for each group. This setting is independent from `GroupX.groupID`, so you can have different groups with the same role.
* `Group.nbrOfDays` as the number of days that the scenario will be run for (in a future version of this model, this could be derived from the `Scenario.endTime` setting)

## Publications

For more information on the underlying model, please refer to our paper. If you use this model or code in your own work, we are happy to receive a citation.

Milan Schmittner, Max Maass, Tom Schons, and Matthias Hollick, “**Reverse Engineering Human Mobility in Large-scale Natural Disasters**,” in *ACM International Conference on Modeling, Analysis and Simulation of Wireless and Mobile Systems (MSWiM)*, November 2017, Miami Beach, USA.
