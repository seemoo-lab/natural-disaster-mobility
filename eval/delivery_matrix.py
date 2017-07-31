import collections
import re

import matplotlib.pyplot as plt
import numpy as np

from names import *
from average import average_configs
from filter import filtered_split_reader


def group_from_hostname(hostname):
    match = re.match(r"([a-z]+)([0-9]+)", hostname, re.I)
    if match:
        return match.groups()[0]
    else:
        return None


def generate_delivery_matrix(created_filename, delivered_filename, outfile_name, prefixes=[['M']]):
    with open(created_filename, 'r') as created_file, \
            open(delivered_filename, 'r') as delivered_file, \
            open(outfile_name, 'w') as outfile:
        created = collections.defaultdict(int)
        for message in iter(filtered_split_reader(created_file, prefixes)):
            source, destination = (message[3], message[4])
            # assume host name in the form 'grp123' where 'grp' identifies the host group
            pair = (group_from_hostname(source), group_from_hostname(destination))
            created[pair] += 1

        delivered = collections.defaultdict(int)
        for message in iter(filtered_split_reader(delivered_file, prefixes)):
            source, destination = (message[5], message[6])
            # assume host name in the form 'grp123' where 'grp' identifies the host group
            pair = (group_from_hostname(source), group_from_hostname(destination))
            delivered[pair] += 1

        pdr = {}
        for pair, count in created.iteritems():
            if pair in delivered:
                pdr[pair] = float(delivered[pair]) / count
            else:
                pdr[pair] = 0.0

        sorted_pdr = sorted(pdr.items())
        for item in sorted_pdr:
            outfile.write(str(item[0][0]) + ' ' + str(item[0][1]) + ' ' + str(item[1]) + '\n')


def generate_delivery_matrix_report(tuple):
    created_file = report_file_from_tuple(tuple, CREATED_REPORT)
    delivered_file = report_file_from_tuple(tuple, DELIVERED_REPORT)
    outfile = report_file_from_tuple(tuple, DELIVERY_MATRIX_REPORT)
    generate_delivery_matrix(created_file, delivered_file, outfile)


def generate_delivery_matrix_reports(config):
    for tuple in expand_config(config):
        generate_delivery_matrix_report(tuple)


def average_delivery_matrix(config):
    average_configs(config, DELIVERY_MATRIX_REPORT)


def type_to_integer_list(list):
    u = np.unique(list)
    d = dict([(u, i) for i, u in enumerate(u)])
    return np.array([d[x] for x in list])


def plot_delivery_matrix(report_file, plot_file, idx=None):
    import matplotlib.colors as colors
    cmap = 'magma'
    fig, ax = plt.subplots(nrows=1, ncols=1, figsize=(4, 4))
    dat = np.genfromtxt(report_file, dtype=None, delimiter=' ', skip_header=0)
    dat = zip(*dat)
    x = dat[0]
    y = dat[1]
    z = dat[2]
    xu = np.unique(x)
    yu = np.unique(y)
    xi = type_to_integer_list(x)
    yi = type_to_integer_list(y)
    grid = np.zeros((len(xu), len(yu)))
    grid[xi, yi] = z

    if idx is not None:
        # sort on axis 0
        grid = np.array(grid)[idx]
        # sort on axis 1 using advanced slicing
        grid[:, range(len(idx))] = grid[:, idx]
        xu = xu[idx]

    ax.pcolor(grid, norm=colors.Normalize(vmin=0, vmax=1), cmap=plt.get_cmap(cmap))
    for x, row in enumerate(grid):
        for y, val in enumerate(row):
            color = 'black' if val > 0.5 else 'white'
            ax.annotate('{:.2f}'.format(float(val)), (y + 0.5, x + 0.5), color=color, ha="center", va="center")

    titles = [pretty_title(t) for t in xu]
    indices = np.arange(len(titles)) + 0.5
    plt.xticks(indices, titles, rotation='vertical')
    plt.yticks(indices, titles)
    ax.set_xlabel('Receiver')
    ax.set_ylabel('Sender')
    ax.xaxis.tick_top()
    ax.xaxis.set_label_position('top')
    # don't show tick markers
    ax.tick_params(axis=u'both', which=u'both', length=0)
    plt.gca().invert_yaxis()
    fig.tight_layout()
    fig.savefig(plot_file, bbox_inches='tight', pad_inches=0, dpi=300)
    fig.clf()
    plt.clf()
    plt.close()


def plot_all_delivery_matrix(config):
    report_files = report_files_from_config(config, DELIVERY_MATRIX_REPORT)
    plot_files = plot_files_from_config(config, DELIVERY_MATRIX_REPORT)
    for (report_file, plot_file) in zip(report_files, plot_files):
        plot_delivery_matrix(report_file, plot_file, idx=[3, 2, 4, 1, 6, 5, 0])
