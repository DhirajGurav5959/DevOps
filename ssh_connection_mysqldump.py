import paramiko

import sys

results = []


def ssh_conn():
    client = paramiko.SSHClient()
    client.load_system_host_keys()
    client.connect(hostname='8.138.89.23', username='admin_user', password='Validus123')

    ssh_stdin, ssh_stdout, ssh_stderr = client.exec_command('mysqldump -u root -p VNWP_NewDB > demo2.sql')
    for line in ssh_stdout:
        results.append(line.strip('\n'))


ssh_conn()

for i in results:
    print(i.strip())

sys.exit()

