from names import *


def average_configs(config, report, outfile_prefix=None):
    if outfile_prefix is None:
        outfile_prefix = config
    report_files = report_files_from_config(config, report)
    average_file = report_file_from_tuple(base_config(outfile_prefix), report)
    average_vals(report_files, average_file)


# def average_vals(infiles, outfile, maxcols=None):
#     import numpy as np
#     inputs = [np.genfromtxt(infile, delimiter=' ', skip_header=0) for infile in infiles]
#     average = np.mean(np.array(inputs), axis=0)
#     np.savetxt(outfile, average, delimiter=' ')


def try_divide(n, d):
    try:
        return n / d
    except TypeError:
        return n


def average_vals(infiles, outfile, maxcols=None):
    """
    Calculates average over timeseries values

    :param infiles: intput files, averages are calculated starting from the second column
    :param outfile: output file containing averages of all values
    :param maxcols: maximum number of columns to average over
    :return: None
    """
    handles = [open(f, 'r') for f in infiles]
    iterators = [iter(h) for h in handles]
    warncount = 1
    with open(outfile, 'w') as outfile:
        while True:
            accum = None
            active = len(iterators)
            for it in iterators:
                try:
                    fields = it.next().split()
                    if maxcols is None:
                        cols = len(fields)
                    else:
                        cols = min(len(fields), maxcols)
                    if accum is None:
                        accum = [0 for _ in range(cols)]
                    for i in range(cols):
                        try:
                            accum[i] += float(fields[i])
                        except ValueError:
                            if warncount > 0:
                                print "Warning: field in column " + repr(i) + " is not a float"
                                warncount -= warncount
                            accum[i] = fields[i]
                except StopIteration:
                    active -= 1
            if active == 0:
                break
            avg = [try_divide(s, active) for s in accum]
            for a in avg:
                outfile.write(str(a) + ' ')
            outfile.write('\n')

    for h in handles:
        h.close()
