import dataiku
import sys
import os

host = sys.argv[1]
apiKey = sys.argv[2]
project = sys.argv[3]
bundle_file = sys.argv[4] + '.zip'

dataiku.set_remote_dss(host, apiKey)
client = dataiku.api_client()

# Import bundle
if not (os.path.exists(bundle_file)):
    print("Bundle file named ", bundle_file, " does not exist, cancelling")
    sys.exit(1)
bundle_file_stream = open(bundle_file, 'rb')

if project in client.list_project_keys():
    test_project = client.get_project(project)
    test_project.import_bundle_from_stream(bundle_file_stream)
else:
    client.create_project_from_bundle_archive(bundle_file_stream)
    test_project = client.get_project(project)
