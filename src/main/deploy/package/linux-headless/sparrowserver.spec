Summary: Sparrow Server
Name: sparrowserver
Version: ${version}
Release: 1
License: ASL 2.0
Vendor: Unknown

%if "x" != "x"
URL: https://sparrowwallet.com
%endif

%if "x/opt" != "x"
Prefix: /opt
%endif

Provides: sparrowserver
Obsoletes: sparrow <= 2.1.4

%if "xutils" != "x"
Group: utils
%endif

Autoprov: 0
Autoreq: 0

#comment line below to enable effective jar compression
#it could easily get your package size from 40 to 15Mb but
#build time will substantially increase and it may require unpack200/system java to install
%define __jar_repack %{nil}

# on RHEL we got unwanted improved debugging enhancements
%define _build_id_links none

%define package_filelist %{_builddir}/%{name}.files
%define app_filelist %{_builddir}/%{name}.app.files
%define filesystem_filelist %{_builddir}/%{name}.filesystem.files

%define default_filesystem / /opt /usr /usr/bin /usr/lib /usr/local /usr/local/bin /usr/local/lib

%description
Sparrow Server

%global __os_install_post %{nil}

%prep

%build

%install
rm -rf %{buildroot}
install -d -m 755 %{buildroot}/opt/sparrowserver
cp -r %{_sourcedir}/opt/sparrowserver/* %{buildroot}/opt/sparrowserver
if [ "$(echo %{_sourcedir}/lib/systemd/system/*.service)" != '%{_sourcedir}/lib/systemd/system/*.service' ]; then
  install -d -m 755 %{buildroot}/lib/systemd/system
  cp %{_sourcedir}/lib/systemd/system/*.service %{buildroot}/lib/systemd/system
fi
%if "x%{_rpmdir}/../../LICENSE" != "x"
  %define license_install_file %{_defaultlicensedir}/%{name}-%{version}/%{basename:%{_rpmdir}/../../LICENSE}
  install -d -m 755 "%{buildroot}%{dirname:%{license_install_file}}"
  install -m 644 "%{_rpmdir}/../../LICENSE" "%{buildroot}%{license_install_file}"
%endif
(cd %{buildroot} && find . -path ./lib/systemd -prune -o -type d -print) | sed -e 's/^\.//' -e '/^$/d' | sort > %{app_filelist}
{ rpm -ql filesystem || echo %{default_filesystem}; } | sort > %{filesystem_filelist}
comm -23 %{app_filelist} %{filesystem_filelist} > %{package_filelist}
sed -i -e 's/.*/%dir "&"/' %{package_filelist}
(cd %{buildroot} && find . -not -type d) | sed -e 's/^\.//' -e 's/.*/"&"/' >> %{package_filelist}
%if "x%{_rpmdir}/../../LICENSE" != "x"
  sed -i -e 's|"%{license_install_file}"||' -e '/^$/d' %{package_filelist}
%endif

%files -f %{package_filelist}
%if "x%{_rpmdir}/../../LICENSE" != "x"
  %license "%{license_install_file}"
%endif

%post
package_type=rpm

%pre
package_type=rpm

%preun
package_type=rpm

%clean
