from names import *
from filter import filtered_split_reader
from series import plot_xy


def generate_combined_delay_cdf_report(created_filenames, delivered_filenames, out_filename):
    combined_created = 0
    combined_delivered = []
    for (created_filename, delivered_filename) in zip(created_filenames, delivered_filenames):
        created, delivered = generate_delay(created_filename, delivered_filename)
        combined_created += created
        combined_delivered += delivered
    write_delay_cdf(combined_created, combined_delivered, out_filename)


def generate_delay(created_filename, delivered_filename, prefixes=None):
    if prefixes is None:
        prefixes = [['M']]
    with open(created_filename, 'r') as created_file, \
            open(delivered_filename, 'r') as delivered_file:
        created = {}
        unique_deliveries = {}
        for fields in filtered_split_reader(created_file, prefixes):
            m_id = fields[1]
            t_sent = float(fields[0])
            created[m_id] = t_sent
            unique_deliveries[m_id] = set()
        delivered = []
        for fields in filtered_split_reader(delivered_file, prefixes):
            last_in_path = fields[-1].split('->')[-1]
            m_id = fields[1]
            if last_in_path in unique_deliveries[m_id]:
                continue
            unique_deliveries[m_id].add(last_in_path)
            t_recv = float(fields[0])
            t_sent = created[m_id]
            delay = t_recv - t_sent
            delivered.append(delay)
        delivered.sort()
        return len(created), delivered


def write_delay_cdf(created, delivered, out_filename):
    delivered.sort()
    with open(out_filename, 'w') as outfile:
        outfile.write(str(0) + ' ' + str(0) + '\n')
        for (idx, val) in enumerate(delivered):
            outfile.write(str(val) + ' ' + str(float(idx) / created) + '\n')


def combine_delays(config):
    created_files = report_files_from_config(config, CREATED_REPORT)
    delivered_files = report_files_from_config(config, DELIVERED_REPORT)
    outfile = report_file_from_tuple(base_config(config), DELAY_CDF_REPORT)
    generate_combined_delay_cdf_report(created_files, delivered_files, outfile)


def plot_delay_cdf(config, plot_prefix=None, titles=None):
    if titles is None:
        titles = config
    if plot_prefix is None:
        plot_prefix = config
    report_files = report_files_from_config(config, DELAY_CDF_REPORT)
    plot_file = plot_file_from_tuple(base_config(plot_prefix), DELAY_CDF_REPORT)
    plot_xy(report_files, plot_file,
            titles=[make_title(c) for c in expand_config(titles)],
            xticks=1,
            xscale=60 * 60,
            xlabel='Delivery Delay [h]',
            ylabel='Messages')
