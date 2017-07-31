def filtered_comments_reader(file, comment='#'):
    for line in file:
        if line.startswith(comment):
            continue
        else:
            yield line


def filtered_split_reader(file, prefixes):
    for line in filtered_comments_reader(file):
        yield line.split()


#def filtered_split_reader(file, prefixes):
#    for line in filtered_comments_reader(file):
#        fields = line.split()
#        if fields[1][0] in prefixes:
#            yield fields
