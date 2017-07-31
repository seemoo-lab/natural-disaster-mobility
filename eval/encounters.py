from names import *
from series import plot_xy
from average import average_configs


def read_unique_encounters(infile):
    unique_encounters_count = {}
    with open(infile, 'r') as file:
        for line in file:
            (node, encounters, unique_encounters) = line.split()
            node = int(node)
            unique_encounters = int(unique_encounters)
            if not node in unique_encounters_count:
                unique_encounters_count[node] = 0
            unique_encounters_count[unique_encounters] = unique_encounters_count.setdefault(unique_encounters, 0) + 1
    return unique_encounters_count


def write_unique_encounters(outfile, unique_encounters_count):
    with open(outfile, 'w') as file:
        for key in sorted(unique_encounters_count.keys()):
            file.write(str(key) + " " + str(unique_encounters_count[key]) + "\n")


def generate_unique_encounters_report(config):
    encounters_report = report_file_from_tuple(config, ENCOUNTERS_REPORT)
    unique_encounters_report = report_file_from_tuple(config, UNIQUE_ENCOUNTERS_REPORT)
    write_unique_encounters(unique_encounters_report, read_unique_encounters(encounters_report))


def generate_unique_encounters_reports(config):
    for c in expand_config(config):
        generate_unique_encounters_report(c)


def average_unique_encounters(config):
    average_configs(config, UNIQUE_ENCOUNTERS_REPORT)


def plot_unique_encounters(config, plot_prefix=None, titles=None):
    if titles is None:
        titles = config
    if plot_prefix is None:
        plot_prefix = config
    report_files = report_files_from_config(config, UNIQUE_ENCOUNTERS_REPORT)
    plot_file = plot_file_from_tuple(base_config(plot_prefix), UNIQUE_ENCOUNTERS_REPORT)
    plot_xy(report_files, plot_file,
            titles=[make_title(c) for c in expand_config(titles)],
            xticks=100,
            xlabel='Unique Encounters',
            ylabel='Node Count')


def average_encounters(config):
    average_configs(config, ENCOUNTERS_REPORT)


def plot_encounters(config, plot_prefix=None, titles=None):
    if titles is None:
        titles = config
    if plot_prefix is None:
        plot_prefix = config
    report_files = report_files_from_config(config, ENCOUNTERS_REPORT)
    plot_file = plot_file_from_tuple(base_config(plot_prefix), ENCOUNTERS_REPORT)
    intervals = [
        (  0,  20, 0.65, "Scientists"),
        ( 20,  60, 0.6, "Injured P."),
        ( 60, 260, 0.65, "Healthy P."),
        (260, 310, 0.5, "USRTs"),
        (310, 360, 0.35, "Govt.  Off."),
        (360, 400, 0.6, "UN Off."),
        (400, 500, 0.6, "DROs")
    ]
    plot_xy(report_files, plot_file,
            titles=[make_title(c) for c in expand_config(titles)],
            xticks=100,
            xlabel='Node ID',
            ylabel='Encounters',
            mark_intervals=intervals,
            size_inches=(5.5, 2.8))
