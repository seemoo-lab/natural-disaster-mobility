import collections

import matplotlib.pyplot as plt
import numpy as np

from names import *
from average import average_configs
from filter import filtered_split_reader


def plot_xy(infiles, outfile, titles=None, xscale=1, xticks=24, yscale=1, xlabel=None, ylabel=None, grid=True, mark_intervals=None, size_inches=(4.0, 2.8)):
    fig = plt.figure()
    ax = fig.add_subplot(111)
    for i, infile in enumerate(infiles):
        dat = np.loadtxt(infile, comments='#')
        x = dat[:, 0] / xscale
        y = dat[:, 1] / yscale
        if titles is None:
            ax.plot(x, y)
        else:
            ax.plot(x, y, label=titles[i])
    ax.margins(xmargin=0)
    if xlabel is not None:
        ax.set_xlabel(xlabel)
    if ylabel is not None:
        ax.set_ylabel(ylabel)
    if isinstance(xticks, (list, tuple)):
        # set explicit list with xticks
        ax.set_xticks(xticks)
    elif xticks is not None:
        # we assume it is a number and treat it as an interval
        ax.set_xticks(range(0, np.ceil(np.amax(x) + xticks).astype(int), xticks))
    if mark_intervals is not None:
        for interval in mark_intervals:
            ax.axvspan(interval[0], interval[1], color='black', fill=False, linewidth=1)
            if len(interval) > 2:
                xcenter = (interval[0] + interval[1]) / 2
                ymin, ymax = ax.get_ylim()
                ycenter = (ymin + ymax) * interval[2]
                ax.text(xcenter, ycenter, str(interval[3]), rotation='vertical', horizontalalignment='center', verticalalignment='center')
    if grid:
        ax.grid(color='#bbbbbb', linestyle='dashed', linewidth=0.5)
    if titles is not None:
        ax.legend(loc='best', ncol=3)
    fig.set_size_inches(size_inches)
    fig.tight_layout()
    fig.savefig(outfile, bbox_inches='tight', pad_inches=0, dpi=300)
    fig.clf()
    plt.clf()
    plt.close()


def generate_delivery_over_time_report(tuple):
    created_file = report_file_from_tuple(tuple, CREATED_REPORT)
    delivered_file = report_file_from_tuple(tuple, DELIVERED_REPORT)
    outfile = report_file_from_tuple(tuple, DELIVERY_TIME_REPORT)
    generate_running_delivery_ttl(created_file, delivered_file, outfile, prefixes=[['M']], fixedres=60)


def generate_delivery_over_time_reports(config):
    for tuple in expand_config(config):
        generate_delivery_over_time_report(tuple)


def average_delivery_over_time(config):
    average_configs(config, DELIVERY_TIME_REPORT)


def plot_delivery_over_time(config, plot_prefix=None, titles=None):
    if titles is None:
        titles = config
    if plot_prefix is None:
        plot_prefix = config
    report_files = report_files_from_config(config, DELIVERY_TIME_REPORT)
    plot_file = plot_file_from_tuple(base_config(plot_prefix), DELIVERY_TIME_REPORT)
    plot_xy(report_files,
            plot_file,
            titles=[make_title(c) for c in expand_config(titles)],
            xscale=60 * 60 * 24,
            xticks=1,
            xlabel='Time [days]',
            ylabel='Delivery Probability')


def generate_running_delivery(created_filename, delivered_filename, outfile_name, prefixes, fixedres=None):
    with open(created_filename, 'r') as created_file, \
            open(delivered_filename, 'r') as infile, \
            open(outfile_name, 'w') as outfile:
        created = 0
        created_iter = iter(filtered_split_reader(created_file, prefixes))
        try:
            created_last_timestamp = float(created_iter.next()[0])
        except StopIteration:
            created_last_timestamp = float('inf')
        delivered = 0
        delivered_iter = iter(filtered_split_reader(infile, prefixes))
        try:
            delivered_last_timestamp = float(delivered_iter.next()[0])
        except StopIteration:
            delivered_last_timestamp = float('inf')
        last_write = float(0)
        while created_last_timestamp != float('inf') or delivered_last_timestamp != float('inf'):
            current_time = min(created_last_timestamp, delivered_last_timestamp)
            if delivered_last_timestamp >= created_last_timestamp:
                created += 1
                try:
                    created_last_timestamp = float(created_iter.next()[0])
                except StopIteration:
                    created_last_timestamp = float('inf')
            else:
                delivered += 1
                try:
                    delivered_last_timestamp = float(delivered_iter.next()[0])
                except StopIteration:
                    delivered_last_timestamp = float('inf')
            delivery = float(delivered) / created
            if fixedres is None:
                outfile.write(str(current_time) + ' ' + str(delivery) + '\n')
            else:
                while last_write + fixedres < current_time:
                    last_write += fixedres
                    outfile.write(str(last_write) + ' ' + str(delivery) + '\n')


def generate_running_delivery_ttl(created_filename, delivered_filename, outfile_name, prefixes, fixedres=None):
    with open(created_filename, 'r') as created_file, \
            open(delivered_filename, 'r') as infile, \
            open(outfile_name, 'w') as outfile:
        created = 0
        created_list = collections.deque()
        created_iter = iter(filtered_split_reader(created_file, prefixes))
        try:
            next_created = created_iter.next()
            created_last_timestamp = float(next_created[0])
            created_last_ttl = float(next_created[5]) * 60
            created_last_id = next_created[1]
            created_list.append((created_last_timestamp + created_last_ttl, created_last_id))
        except StopIteration:
            created_last_timestamp = float('inf')
        delivered = 0
        delivered_ids = set()
        delivered_iter = iter(filtered_split_reader(infile, prefixes))
        try:
            next_delivered = delivered_iter.next()
            delivered_last_timestamp = float(next_delivered[0])
            delivered_last_id = next_delivered[1]
            delivered_ids.add(delivered_last_id)
        except StopIteration:
            delivered_last_timestamp = float('inf')
        last_write = 0
        while created_last_timestamp != float('inf') or delivered_last_timestamp != float('inf'):
            current_time = min(created_last_timestamp, delivered_last_timestamp)
            if delivered_last_timestamp >= created_last_timestamp:
                created += 1
                try:
                    next_created = created_iter.next()
                    created_last_timestamp = float(next_created[0])
                    created_last_ttl = float(next_created[5]) * 60  # TTL is in min
                    created_last_id = next_created[1]
                    created_list.append((created_last_timestamp + created_last_ttl, created_last_id))
                except StopIteration:
                    created_last_timestamp = float('inf')
            else:
                delivered += 1
                try:
                    next_delivered = delivered_iter.next()
                    delivered_last_timestamp = float(next_delivered[0])
                    delivered_last_id = next_delivered[1]
                    delivered_ids.add(delivered_last_id)
                except StopIteration:
                    delivered_last_timestamp = float('inf')

            if fixedres is None:
                while len(created_list) > 0 and created_list[0][0] < current_time:
                    (expired, id) = created_list.popleft()
                    created -= 1
                    if id in delivered_ids:
                        delivered -= 1
                try:
                    delivery = float(delivered) / created
                except ZeroDivisionError:
                    delivery = 0
                outfile.write(str(current_time) + ' ' + str(delivery) + '\n')
            else:
                while last_write + fixedres < current_time:
                    last_write += fixedres
                    while len(created_list) > 0 and created_list[0][0] < last_write:
                        (expired, id) = created_list.popleft()
                        created -= 1
                        if id in delivered_ids:
                            delivered -= 1
                    try:
                        delivery = float(delivered) / created
                    except ZeroDivisionError:
                        delivery = 0
                    outfile.write(str(last_write) + ' ' + str(delivery) + '\n')
