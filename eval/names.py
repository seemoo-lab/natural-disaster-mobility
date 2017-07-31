import os
import itertools

from util import create_dir_if_not_exists


SCENARIO = 'Tacloban'
CREATED_REPORT = 'CreatedMessagesReport'
DELIVERED_REPORT = 'DeliveredMessagesReport'
DELIVERY_TIME_REPORT = 'DeliveredOverTimeReport'
DELAY_CDF_REPORT = 'CombinedDelayCDF'
BUFFER_REPORT = 'BufferOccupancyReport'
UNIQUE_ENCOUNTERS_REPORT = 'UniqueEncountersDistributionReport'
ENCOUNTERS_REPORT = 'EncountersVSUniqueEncountersReport'
NODE_DENSITY_REPORT = 'NodeDensityReport'
DELIVERY_MATRIX_REPORT = 'DeliveryMatrixReport'

TITLES_REPLACEMENTS = {'ProphetRouter': 'Prpht',
          'SprayAndWaitRouter': 'S+W',
          'EpidemicRouter': 'Epdmc',
          'NaturalDisasterMovementModel': 'ND',
          'MapBasedMovement': 'Map',
          'RandomWaypoint': 'RWP',
          'sci': 'Scientist',
          'pi': 'Injured P.',
          'ph': 'Healthy P.',
          'sr': 'USRTs',
          'gov': 'Govt. Off.',
          'un': 'UN Off.',
          'dro': 'DROs',
}

exp_dir = ''
REPORT_SUFFIX = 'txt'
PLOT_SUFFIX = 'pdf'


def report_dir():
    return os.path.join(exp_dir, 'reports')


def plot_dir():
    return os.path.join(exp_dir, 'plots')


def report_file_from(report_name):
    return SCENARIO + '_' + report_name + '.' + REPORT_SUFFIX


def plot_file_from(report_name, suffix=PLOT_SUFFIX):
    return SCENARIO + '_' + report_name + '.' + suffix


def exp_string(settings):
    exp_str = ''
    for setting in settings:
        exp_str += str(setting) + '-'
    return exp_str[:-1]


def pretty_title(str):
    return TITLES_REPLACEMENTS.get(str, str)


def make_title(title_components):
    title = ''
    for component in title_components:
        title += pretty_title(component) + '-'
    return title[:-1]


def expand_config(config):
    return itertools.product(*config)


def report_file_from_tuple(tuple, report):
    report_file = os.path.join(report_dir(), exp_string(tuple), report_file_from(report))
    create_dir_if_not_exists(report_file, isfile=True)
    return report_file


def report_files_from_config(config, report):
    return [report_file_from_tuple(c, report) for c in expand_config(config)]


def base_config(config):
    """
    Try to extract a base configuration, i.e., consisting of those settings that have only a single value
    Can be useful to determine plot titles automatically
    :param config:
    :return:
    """
    return [e[0] for e in config if len(e) == 1]


def plot_file_from_tuple(tuple, report, suffix=PLOT_SUFFIX):
    plot_file = os.path.join(plot_dir(), exp_string(tuple), plot_file_from(report, suffix))
    create_dir_if_not_exists(plot_file, isfile=True)
    return plot_file


def plot_files_from_config(config, report, suffix=PLOT_SUFFIX):
    return [plot_file_from_tuple(c, report, suffix) for c in expand_config(config)]
