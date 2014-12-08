import sys
import json as json
import csv

def flatten(structure, key="", path="", flattened=None):
    if flattened is None:
        flattened = {}
    if type(structure) not in(dict, list):
        flattened[((path + "_") if path else "") + key] = structure #" ".join(str(structure).strip().split())
    elif isinstance(structure, list):
        for i, item in enumerate(structure):
            flatten(item, "%d" % i, "_".join(filter(None,[path,key])), flattened)
    else:
        for new_key, value in structure.items():
            flatten(value, new_key, "_".join(filter(None,[path,key])), flattened)
    return flattened

def main():
    ifilename = sys.argv[1]
    try:
        ofilename = sys.argv[2]
    except:
        ofilename = ifilename + ".csv"

    # LOAD DATA
    json_lines = [json.loads( l.strip() ) for l in open(ifilename).readlines() ]

    csv_lines = []
    for l in json_lines:
        try:
            flattened = flatten(l)
            #if "business" in ifilename and len(flattened) < 10: continue
        except:
            pass
        csv_lines.append(flattened)


    fieldnames = csv_lines[0].keys()
    writer = csv.DictWriter(open(ofilename, "w"), fieldnames=fieldnames, delimiter="\t")
    writer.writerow(dict(zip(fieldnames, fieldnames)))

    for entry in csv_lines:
        try:
            writer.writerow(entry)
        except Exception as e:
            print(e.message)