from setuptools import setup, find_packages

setup(
    name='sentiment-analysis',
    version='0.1',
    description='A sample Python package',
    author='Wini Akam',
    author_email='winignatius43@gmail.com',
    packages=find_packages(exclude=['.venv*', '*.egg-info*']),
)