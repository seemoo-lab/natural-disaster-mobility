#!/usr/bin/env python

# Evaluation script for paper
#   Milan Schmittner, Max Maass, Tom Schons, and Matthias Hollick,
#   "Reverse Engineering Human Mobility
#    in Large-scale Natural Disasters,"
#   to appear in ACM MSWiM'17.
#
# Copyright (C) 2017  Secure Mobile Networking Lab (SEEMOO)
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <http://www.gnu.org/licenses/>.

import sys

from eval import *

movements = ["NaturalDisasterMovementModel", "MapBasedMovement", "RandomWaypoint"]
routers = ["EpidemicRouter"]
runs = range(10)

settings = {
    "Group.movementModel": movements,
    "Group.router": routers,
    "MovementModel.rngSeed": runs,
}


def plot_all():
    print "Average delivery over time"
    generate_delivery_over_time_reports((movements, routers, runs))
    for movement in movements:
        for router in routers:
            average_delivery_over_time(([movement], [router], runs))
    print "Plot delivery over time"
    plot_delivery_over_time((movements, routers), plot_prefix=[""], titles=(movements,))

    print "Combine delays"
    for movement in movements:
        for router in routers:
            combine_delays(([movement], [router], runs))
    print "Plot delay CDF"
    plot_delay_cdf((movements, routers), plot_prefix=[""], titles=(movements,))

    print "Average buffer occupancy"
    for movement in movements:
        for router in routers:
            average_buffer_occupancy(([movement], [router], runs))
    print "Plot buffer occupancy"
    plot_buffer_occupancy((movements, routers), plot_prefix=[""],  titles=(movements,))

    print "Average delivery matrix"
    generate_delivery_matrix_reports((movements, routers, runs))
    for movement in movements:
        for router in routers:
            average_delivery_matrix(([movement], [router], runs))
    print "Plot delivery matrix"
    plot_all_delivery_matrix((movements, routers))

    print "Average unique encounters"
    generate_unique_encounters_reports((movements, [routers[0]], runs))  # report is independent of router
    for movement in movements:
        average_unique_encounters(([movement], [routers[0]], runs))  # report is independent of router
    print "Plot unique encounters"
    plot_unique_encounters((movements, [routers[0]]), plot_prefix=[""], titles=(movements,))

    print "Average total encounters"
    for movement in movements:
        average_encounters(([movement], [routers[0]], runs))  # report is independent of router
    print "Plot total encounters"
    plot_encounters((movements, [routers[0]]), plot_prefix=[""], titles=(movements,))

    print "Average node density"
    for movement in movements:
        average_node_density(([movement], [routers[0]], runs))  # only once per movement model
    print "Plot node density"
    plot_all_node_density((movements, [routers[0]]), plot_prefixes=(movements,))


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:], settings, plot_all))
