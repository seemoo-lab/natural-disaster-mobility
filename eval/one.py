import multiprocessing
import subprocess
import sys
import argparse
import os
import shutil
from datetime import datetime
from glob import glob

from util import create_dir_if_not_exists
import names


def expand_settings(flat_setting, settings):
    if len(settings.keys()) == 0:
        return [flat_setting]
    key = settings.keys()[0]
    list_of_flat_settings = []
    for value in settings[key]:
        # add new element to current flat setting
        new_flat_setting = flat_setting.copy()
        new_flat_setting[key] = value
        # remove current key from new dictionary
        new_settings = settings.copy()
        del new_settings[key]
        # call recursively
        list_of_flat_settings += expand_settings(new_flat_setting, new_settings)
    return list_of_flat_settings


def replace_settings_in_template(one_template, one_setting, flat_setting):
    with open(one_setting, "wt") as fout:
        with open(one_template, "rt") as fin:
            for line in fin:
                entry = line.split(" ")[0]
                if entry in flat_setting:
                    new_line = entry + " = " + str(flat_setting[entry]) + "\n"
                else:
                    new_line = line
                fout.write(new_line)


def construct_call(settings_file, one_dir):
    return [os.path.join(one_dir, "one.sh"), "-b 1", settings_file]


def evaluate(work_dir, plot_all):
    names.exp_dir = work_dir
    plot_dir = os.path.join(work_dir, 'plots')
    create_dir_if_not_exists(plot_dir, isfile=False)
    plot_all()


def parse_args(argv):
    parser = argparse.ArgumentParser()
    parser.add_argument("-d", "--description",
                        help="Experiment description that is added to timestamp of the experiment folder.")
    parser.add_argument("-t", "--test", action='store_true', help="Set output dir to 'test' (overwriting old results).")
    parser.add_argument("-s", "--skipbuild", action='store_true', help="Skip build.")
    parser.add_argument("--config", help="The ONE scenario configuration (.txt file)",
                        default="tacloban_settings.txt")
    parser.add_argument("-p", "--plot", action='store_true',
                        help="Only plot everything in the current dir (ignores all other arguments).")
    parser.add_argument("--onedir", help="Location of one's base directory", default="./")
    parser.add_argument("--outdir", help="Location of output directory", default="out/")
    return parser.parse_args(argv)


def copy_asset(file, dir):
    target = os.path.join(dir, file)
    create_dir_if_not_exists(target, isfile=True)
    shutil.copy2(file, target)


def move_asset(file, dir):
    target = os.path.join(dir, file)
    create_dir_if_not_exists(target, isfile=True)
    shutil.move(file, target)


def create_work_dir(args):
    if args.test:
        work_dir = os.path.abspath(os.path.join(args.outdir, "test"))
    else:
        work_dir = os.path.abspath(os.path.join(args.outdir,
                                                datetime.now().strftime('%Y-%m-%d_%H.%M.%S') + \
                                                ("_" + args.description if args.description else "")))
    create_dir_if_not_exists(work_dir, isfile=False)
    return work_dir


def exp_string_from_flat_settings(flat_settings):
    exp_string = ""
    for key in flat_settings:
        exp_string += str(flat_settings[key]) + '-'
    return exp_string[:-1]


def create_exp_settings_file(work_dir, settings_base_file, flat_setting):
    settings_base_file_name = os.path.split(settings_base_file)[-1]
    exp_string = exp_string_from_flat_settings(flat_setting)
    exp_dir = os.path.join(work_dir, "experiments", exp_string)
    create_dir_if_not_exists(exp_dir, isfile=False)
    flat_setting["Report.reportDir"] = exp_dir
    exp_settings_file = os.path.join(work_dir, exp_dir, settings_base_file_name)
    replace_settings_in_template(settings_base_file, exp_settings_file, flat_setting)
    return exp_settings_file


def build(one_dir):
    result = subprocess.call([os.path.join(one_dir, "compile.sh")])
    if result:
        print >> sys.stderr, "Failed to build the ONE, stop"
        return result


def main(argv, settings, plot_all):
    args = parse_args(argv)

    if args.plot:
        print "##"
        print "## ONLY POST-PROCESSING AND PLOTTING"
        print "##"
        evaluate(work_dir="", plot_all=plot_all)
        return 0

    work_dir = create_work_dir(args)
    start_time = datetime.now()
    print "##"
    print "## START EXPERIMENT @ " + str(start_time)
    print "##   Use working directory: " + work_dir

    if args.skipbuild:
        print "##   Skipping building experiment"
    else:
        print "##   Pre-Build experiment"
        build(args.onedir)

    #
    # Generate single experiments
    #

    # Take git repo status
    with open(os.path.join(work_dir, "rev.git"), "w+") as fout:
        result = subprocess.call(["git", "rev-parse", "HEAD"], stdout=fout)
        if result:
            print "Failed to get git revision"
    with open(os.path.join(work_dir, "diff.git"), "w+") as fout:
        result = subprocess.call(["git", "diff"], stdout=fout)
        if result:
            print "Failed to get git diff"

    # Copy asset files
    settings_base_file = args.config
    assets = ["exp.py", settings_base_file]
    assets.extend(glob("eval/*.py"))
    [copy_asset(asset, work_dir) for asset in assets]

    settings_files = []
    for flat_setting in expand_settings({}, settings):
        exp_settings_file = create_exp_settings_file(work_dir, settings_base_file, flat_setting)
        settings_files.append(exp_settings_file)

    total = len(settings_files)
    print "##   Start " + repr(total) + " experiments on " + repr(multiprocessing.cpu_count()) + " core(s)"
    print "##"
    pool = multiprocessing.Pool(None)  # use 'multiprocessing.cpu_count()' cores

    def log_result(_):
        completed = sum(1 for r in results if r.ready())
        print "[" + str(round(completed * 100.0 / total, 1)) + "%] " + str(completed) + "/" + str(total) + " completed"
        sys.stdout.flush()

    all_jobs = [construct_call(file, args.onedir) for file in settings_files]
    results = [pool.apply_async(subprocess.call, (job,), callback=log_result) for job in all_jobs]
    pool.close()
    # pool.join() cannot be interrupted by Ctrl+C, so we wait for results manually
    # See: http://stackoverflow.com/questions/1408356/keyboard-interrupts-with-pythons-multiprocessing-pool/1408476#1408476
    for r in results:
        r.wait(9999999)
    pool.join()
    print "##   All experiments completed"

    print "##   Evaluate experiments"
    evaluate(work_dir, plot_all)

    # Delete temporary files
    [os.remove(file) for file in settings_files]

    # Copy log file
    if os.path.exists("exp.log"):
        move_asset("exp.log", work_dir)

    end_time = datetime.now()
    print "##"
    print "## END EXPERIMENT @ " + str(end_time)
    print "##   Duration: " + str(end_time - start_time)
    print "##"

    return 0
