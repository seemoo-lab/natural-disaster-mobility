import os


def create_dir_if_not_exists(path, isfile):
    if isfile is True:
        dir = os.path.dirname(path)
    else:
        dir = path
    if not os.path.exists(dir):
        os.makedirs(dir)