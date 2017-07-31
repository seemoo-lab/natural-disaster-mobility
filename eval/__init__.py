# Evaluation scripts for paper
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

from names import *
from util import create_dir_if_not_exists
from average import *
from series import *
from delay import combine_delays, plot_delay_cdf
from buffer_occupancy import average_buffer_occupancy, plot_buffer_occupancy
from delivery_matrix import generate_delivery_matrix_reports, average_delivery_matrix, plot_all_delivery_matrix
from encounters import generate_unique_encounters_reports, average_unique_encounters, average_encounters, plot_unique_encounters, plot_encounters
from node_density import average_node_density, plot_all_node_density
from one import main
