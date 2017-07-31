import matplotlib.pyplot as plt
import numpy as np

from names import *
from average import average_configs


def average_node_density(config):
    average_configs(config, NODE_DENSITY_REPORT)


def plot_node_density(report_file, plot_file, plot_file_scale=None):
    import matplotlib.colors as colors
    # cmap = 'coolwarm'
    # cmap = 'plasma'
    # cmap = 'viridis'
    cmap = 'magma'
    fig, ax = plt.subplots(nrows=1, ncols=1, figsize=(3.7, 5))
    dat = np.genfromtxt(report_file, delimiter=' ', skip_header=0)
    dat.view('f8,f8,f8').sort(order=['f2'], axis=0)
    x = dat[:, 0]
    y = dat[:, 1]
    z = dat[:, 2]
    z.sort()
    zmedian = np.ma.median(np.ma.masked_equal(z, 0))
    zlog = np.log10(z / zmedian)
    zlogmasked = np.ma.masked_invalid(zlog)
    zlog = zlog + abs(zlogmasked.min()) + 1
    ax.set_aspect(1)
    ax.set_facecolor('black')
    ax.set_xlim(0, 500)
    ax.set_ylim(700, 0)
    ax.set_xticks([])
    ax.set_yticks([])
    vmin = np.ma.masked_invalid(z).min()
    vmax = z[-3]  # ignore base camp sleeping spots
    # print movement + ' ' + str(np.ma.masked_invalid(zlog).min()) + '-' + str(zlog.max()) + ' median: ' + str(zmedian) + '\n'
    cax = ax.scatter(x, y, c=z, s=np.square(zlog),
                     norm=colors.SymLogNorm(linthresh=0.001, linscale=0.001, vmin=vmin, vmax=vmax),
                     cmap=plt.get_cmap(cmap))
    if plot_file_scale is None:
        fig.colorbar(cax, ticks=[], label='Relative node density')
    fig.tight_layout()
    fig.savefig(plot_file, bbox_inches='tight', pad_inches=0, dpi=300)
    fig.clf()
    plt.clf()
    plt.close()
    if plot_file_scale is not None:
        fig = plt.figure(figsize=(0.2, 4))
        plt.imshow(np.array([[0, 1]]), cmap=cmap)
        plt.gca().set_visible(False)
        cax = plt.axes([0.0, 0.0, 1, 1])
        plt.colorbar(cax=cax, ticks=[], label='Relative node density')
        fig.savefig(plot_file_scale, bbox_inches='tight', pad_inches=0)


def plot_all_node_density(config, plot_prefixes=None):
    if plot_prefixes is None:
        plot_prefixes = config
    report_files = report_files_from_config(config, NODE_DENSITY_REPORT)
    plot_files = plot_files_from_config(plot_prefixes, NODE_DENSITY_REPORT, suffix='png')
    plot_files_scale = plot_files_from_config(plot_prefixes, NODE_DENSITY_REPORT + '-scale')
    for (report_file, plot_file, plot_file_scale) in zip(report_files, plot_files, plot_files_scale):
        plot_node_density(report_file, plot_file, plot_file_scale)
