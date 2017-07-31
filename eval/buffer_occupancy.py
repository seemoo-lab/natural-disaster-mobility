from names import *
from series import plot_xy
from average import average_configs


def average_buffer_occupancy(config):
    average_configs(config, BUFFER_REPORT)


def plot_buffer_occupancy(config, plot_prefix=None, titles=None):
    if titles is None:
        titles = config
    if plot_prefix is None:
        plot_prefix = config
    report_files = report_files_from_config(config, BUFFER_REPORT)
    plot_file = plot_file_from_tuple(base_config(plot_prefix), BUFFER_REPORT)
    plot_xy(report_files, plot_file,
            titles=[make_title(c) for c in expand_config(titles)],
            xscale=60 * 60 * 24,
            xticks=1,
            xlabel='Time [h]',
            ylabel='Buffer Occupancy')
